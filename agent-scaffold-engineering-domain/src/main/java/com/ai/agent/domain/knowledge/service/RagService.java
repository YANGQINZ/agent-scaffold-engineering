package com.ai.agent.domain.knowledge.service;

import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.repository.IDocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * RAG服务 — 三阶段检索增强生成管线
 * 1. 查询改写
 * 2. 混合检索（向量 + BM25）+ 融合重排
 * 3. 构建增强提示词
 */
@Slf4j
@Service
public class RagService {

    private static final double ALPHA = 0.7; // 向量搜索权重
    private static final double BETA = 0.3;  // BM25搜索权重

    private final IDocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final ChatModel chatModel;

    public RagService(IDocumentChunkRepository chunkRepository,
                      EmbeddingService embeddingService,
                      ChatModel chatModel) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.chatModel = chatModel;
    }

    /**
     * 完整RAG检索管线
     */
    public String retrieveAndBuild(String baseId, String query, int topK) {
        // 阶段1: 查询改写
        String rewrittenQuery = rewriteQuery(query);

        // 阶段2: 混合检索 + 融合重排
        List<DocumentChunk> relevantChunks = hybridSearch(baseId, rewrittenQuery, topK);

        // 阶段3: 构建增强提示词
        return buildEnhancedPrompt(query, relevantChunks);
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
     */
    public List<DocumentChunk> hybridSearch(String baseId, String query, int topK) {
        // 向量搜索
        float[] queryEmbedding = embeddingService.embed(query);
        List<DocumentChunk> vectorResults = chunkRepository.vectorSearch(baseId, queryEmbedding, topK);

        // BM25搜索
        List<DocumentChunk> bm25Results = chunkRepository.bm25Search(baseId, query, topK);

        // 融合重排
        return fuseAndRerank(vectorResults, bm25Results, topK);
    }

    /**
     * 融合重排 — 基于排名的倒数融合（RRF）
     * 使用排名位置作为分数代理: score = 1/rank
     */
    public List<DocumentChunk> fuseAndRerank(List<DocumentChunk> vectorResults,
                                             List<DocumentChunk> bm25Results,
                                             int topK) {
        // 用chunkId作为key追踪分数和分块
        Map<Long, Double> scoreMap = new HashMap<>();
        Map<Long, DocumentChunk> chunkMap = new LinkedHashMap<>();

        // 向量搜索结果打分
        for (int i = 0; i < vectorResults.size(); i++) {
            DocumentChunk chunk = vectorResults.get(i);
            long chunkId = chunk.getChunkId();
            double score = ALPHA / (i + 1.0); // 1/rank加权
            scoreMap.merge(chunkId, score, Double::sum);
            chunkMap.putIfAbsent(chunkId, chunk);
        }

        // BM25搜索结果打分
        for (int i = 0; i < bm25Results.size(); i++) {
            DocumentChunk chunk = bm25Results.get(i);
            long chunkId = chunk.getChunkId();
            double score = BETA / (i + 1.0); // 1/rank加权
            scoreMap.merge(chunkId, score, Double::sum);
            chunkMap.putIfAbsent(chunkId, chunk);
        }

        // 按综合分数降序排列，取topK
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> chunkMap.get(entry.getKey()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 阶段3: 构建增强提示词 — 将检索到的分块作为上下文注入
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
