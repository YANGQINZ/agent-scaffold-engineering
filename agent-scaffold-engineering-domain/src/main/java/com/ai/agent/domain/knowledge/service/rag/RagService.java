package com.ai.agent.domain.knowledge.service.rag;

import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.model.entity.RerankItem;
import com.ai.agent.domain.knowledge.model.valobj.MmrConfig;
import com.ai.agent.domain.knowledge.model.valobj.RagResult;
import com.ai.agent.domain.knowledge.model.valobj.RerankConfig;
import com.ai.agent.domain.knowledge.repository.IDocumentChunkRepository;
import com.ai.agent.domain.knowledge.service.EmbeddingService;
import com.ai.agent.domain.knowledge.service.RerankingService;
import com.ai.agent.domain.knowledge.service.mmr.MmrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * RAG服务 — 三阶段检索增强生成管线
 * 1. 查询改写
 * 2. 混合检索（向量 + BM25）+ 融合重排
 * 3. Reranking精排 + MMR多样性重排
 * 4. 构建增强提示词
 */
@Slf4j
@Service
public class RagService {

    private static final double ALPHA = 0.7; // 向量搜索权重
    private static final double BETA = 0.3;  // BM25搜索权重

    private final IDocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final ChatModel chatModel;
    private final RerankingService rerankingService;
    private final MmrService mmrService;
    private final RerankConfig rerankConfig;
    private final MmrConfig mmrConfig;

    public RagService(IDocumentChunkRepository chunkRepository,
                      EmbeddingService embeddingService,
                      ChatModel chatModel,
                      RerankingService rerankingService,
                      MmrService mmrService,
                      RerankConfig rerankConfig,
                      MmrConfig mmrConfig) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.chatModel = chatModel;
        this.rerankingService = rerankingService;
        this.mmrService = mmrService;
        this.rerankConfig = rerankConfig;
        this.mmrConfig = mmrConfig;
    }

    /**
     * 完整RAG检索管线 — 返回增强提示词和原始切片
     * 供RagDecorator使用，一次调用完成所有阶段
     */
    public RagResult retrieveAndBuild(String baseId, String query, int topK) {
        // 阶段1: 查询改写
        String rewrittenQuery = rewriteQuery(query);

        // 阶段2: 混合检索 + 融合重排（含向量embedding，复用给MMR）
        float[] queryEmbedding = embeddingService.embed(rewrittenQuery);
        List<DocumentChunk> relevantChunks = hybridSearch(baseId, rewrittenQuery, topK, queryEmbedding);

        // 阶段3: Reranking精排 + MMR多样性重排
        relevantChunks = postRetrieveOptimize(rewrittenQuery, relevantChunks, topK, queryEmbedding);

        // 阶段4: 构建增强提示词
        String enhancedPrompt = buildEnhancedPrompt(query, relevantChunks);

        return RagResult.builder()
                .enhancedPrompt(enhancedPrompt)
                .chunks(relevantChunks)
                .degraded(false)
                .build();
    }

    /**
     * RAG检索并返回原始切片（供直接检索场景使用）
     */
    public List<DocumentChunk> retrieve(String baseId, String query, int topK) {
        String rewrittenQuery = rewriteQuery(query);
        float[] queryEmbedding = embeddingService.embed(rewrittenQuery);
        List<DocumentChunk> relevantChunks = hybridSearch(baseId, rewrittenQuery, topK, queryEmbedding);
        return postRetrieveOptimize(rewrittenQuery, relevantChunks, topK, queryEmbedding);
    }

    /**
     * 阶段1: 查询改写 — 将非正式查询转为结构化搜索词
     */
    public String rewriteQuery(String originalQuery) {
        try {
            String systemPrompt = "你是一个查询改写助手。请将用户的非正式查询改写为更精确、更利于检索的搜索关键词。" +
                    "只输出改写后的查询，不要添加任何解释。保留查询的核心意图，使用更规范的术语。";
            Prompt prompt = new Prompt(List.of(
                    new org.springframework.ai.chat.messages.SystemMessage(systemPrompt),
                    new org.springframework.ai.chat.messages.UserMessage(originalQuery)
            ));
            String rewritten = chatModel.call(prompt).getResult().getOutput().getText();
            log.info("查询改写: '{}' -> '{}'", originalQuery, rewritten);
            return rewritten;
        } catch (Exception e) {
            log.warn("查询改写失败，使用原始查询: {}", e.getMessage());
            return originalQuery;
        }
    }

    /**
     * 阶段2: 混合检索 — 向量搜索 + BM25搜索 + 融合重排
     * @param queryEmbedding 查询向量（由调用方提供，避免重复计算）
     */
    private List<DocumentChunk> hybridSearch(String baseId, String query, int topK, float[] queryEmbedding) {
        // 向量搜索（复用外部计算的embedding）
        List<DocumentChunk> vectorResults = chunkRepository.vectorSearch(baseId, queryEmbedding, topK);

        // BM25搜索
        List<DocumentChunk> bm25Results = chunkRepository.bm25Search(baseId, query, topK);

        // 融合重排
        return fuseAndRerank(vectorResults, bm25Results, topK);
    }

    /**
     * 融合重排 — 基于排名的倒数融合（RRF）
     * 使用排名位置作为分数代理: score = 1/rank
     *
     * 注意：设计文档7.2节定义的融合公式为分数归一化+加权求和：
     *   score = alpha * norm(vec_score) + (1-alpha) * norm(bm25_score)
     * 当前实现采用RRF（Reciprocal Rank Fusion），原因是RRF对分数分布不敏感、无需归一化、
     * 在混合检索场景中表现更稳定。偏离设计文档的分数归一化公式，后续如需切换可在此方法中修改。
     */
    public List<DocumentChunk> fuseAndRerank(List<DocumentChunk> vectorResults,
                                             List<DocumentChunk> bm25Results,
                                             int topK) {
        // 默认 RRF 常量，防止排名靠前者权重过大
        double K = 60.0;

        int initialCapacity = (int) ((vectorResults.size() + bm25Results.size()) / 0.75) + 1;
        Map<String, Double> scoreMap = new HashMap<>(initialCapacity);
        Map<String, DocumentChunk> chunkMap = new HashMap<>(initialCapacity);

        // 处理检索结果的统一逻辑
        processResults(vectorResults, scoreMap, chunkMap, ALPHA, K);
        processResults(bm25Results, scoreMap, chunkMap, BETA, K);

        return scoreMap.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> {
                DocumentChunk chunk = chunkMap.get(entry.getKey());
                chunk.setScore(entry.getValue());
                return chunk;
            })
            .toList();
    }

    private void processResults(List<DocumentChunk> results,
                                Map<String, Double> scoreMap,
                                Map<String, DocumentChunk> chunkMap,
                                double weight, double k) {
        if (results == null) return;
        for (int i = 0; i < results.size(); i++) {
            DocumentChunk chunk = results.get(i);
            String id = chunk.getId();
            // RRF 核心公式：Weight / (K + rank)
            double score = weight / (k + i);
            scoreMap.merge(id, score, Double::sum);
            chunkMap.putIfAbsent(id, chunk);
        }
    }

    /**
     * 阶段3: 后检索优化 — Reranking精排 + MMR多样性重排
     * Rerank失败时降级使用RRF结果，不阻断RAG主流程
     * @param queryEmbedding 查询向量（由调用方提供，避免与hybridSearch重复计算）
     */
    private List<DocumentChunk> postRetrieveOptimize(String rewrittenQuery,
                                                     List<DocumentChunk> rrfResults,
                                                     int topK,
                                                     float[] queryEmbedding) {
        List<DocumentChunk> currentResults = rrfResults;

        // 3.1 Reranking精排
        if (Boolean.TRUE.equals(rerankConfig.getEnabled()) && !currentResults.isEmpty()) {
            try {
                List<String> documents = currentResults.stream()
                        .map(DocumentChunk::getContent)
                        .toList();
                int topN = rerankConfig.getTopN() != null ? rerankConfig.getTopN() : topK;
                List<RerankItem> rerankItems = rerankingService.rerank(rewrittenQuery, documents, topN);

                if (rerankItems != null && !rerankItems.isEmpty()) {
                    // 根据Rerank结果重排chunk列表
                    Map<Integer, DocumentChunk> indexMap = new HashMap<>();
                    for (int i = 0; i < currentResults.size(); i++) {
                        indexMap.put(i, currentResults.get(i));
                    }
                    List<DocumentChunk> rerankedChunks = new ArrayList<>();
                    for (RerankItem item : rerankItems) {
                        DocumentChunk chunk = indexMap.get(item.getIndex());
                        if (chunk != null) {
                            // 用Rerank得分替换RRF得分
                            DocumentChunk scoredChunk = DocumentChunk.builder()
                                    .id(chunk.getId())
                                    .content(chunk.getContent())
                                    .embedding(chunk.getEmbedding())
                                    .metadata(chunk.getMetadata())
                                    .score(item.getRelevanceScore())
                                    .build();
                            rerankedChunks.add(scoredChunk);
                        }
                    }
                    if (!rerankedChunks.isEmpty()) {
                        currentResults = rerankedChunks;
                        log.info("Reranking精排完成: 结果数量={}", rerankedChunks.size());
                    }
                } else {
                    log.info("Reranking返回空结果，降级使用RRF融合结果");
                }
            } catch (Exception e) {
                log.warn("Reranking精排异常，降级使用RRF融合结果: {}", e.getMessage());
            }
        }

        // 3.2 MMR多样性重排（复用hybridSearch阶段计算的queryEmbedding）
        if (Boolean.TRUE.equals(mmrConfig.getEnabled()) && !currentResults.isEmpty()) {
            try {
                double lambda = mmrConfig.getLambda() != null ? mmrConfig.getLambda() : 0.7;
                int mmrTopK = mmrConfig.getTopK() != null ? mmrConfig.getTopK() : topK;
                currentResults = mmrService.rerankByMmr(queryEmbedding, currentResults, lambda, mmrTopK);
                log.info("MMR多样性重排完成: 结果数量={}", currentResults.size());
            } catch (Exception e) {
                log.warn("MMR多样性重排异常，使用当前结果: {}", e.getMessage());
            }
        }

        return currentResults;
    }

    /**
     * 阶段4: 构建增强提示词 — 将检索到的分块作为上下文注入
     */
    public String buildEnhancedPrompt(String query, List<DocumentChunk> chunks) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            context.append("【参考材料").append(i + 1).append("】\n");
            context.append(chunks.get(i).getContent());
            context.append("\n\n");
        }

        return context +
                "基于以上参考材料，请回答以下问题。如果参考材料中没有相关信息，请根据你的知识作答，" +
                "并说明参考材料中未找到相关内容。\n\n" +
                "问题：" + query;
    }

}
