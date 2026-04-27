package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.SourceRef;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.model.valobj.RagResult;
import com.ai.agent.domain.knowledge.service.RagService;
import com.ai.agent.types.enums.StreamEventType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RAG装饰器 — 在委托策略执行前执行RAG检索增强，将检索结果注入对话上下文
 * 通过retrieveAndBuild一次性完成检索+增强提示词构建
 * RAG检索异常时自动降级为无RAG模式执行
 */
@Slf4j
public class RagDecorator implements ChatStrategy {

    private static final int DEFAULT_TOP_K = 5;

    private final ChatStrategy delegate;
    private final RagService ragService;

    public RagDecorator(ChatStrategy delegate, RagService ragService) {
        this.delegate = delegate;
        this.ragService = ragService;
    }

    @Override
    public ChatResponse execute(ChatRequest request) {
        String knowledgeBaseId = request.getKnowledgeBaseId();
        String query = request.getQuery();

        // 检查RAG前置条件
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank() || query == null || query.isBlank()) {
            log.warn("RAG前置条件不满足（knowledgeBaseId或query为空），跳过RAG直接执行");
            ChatResponse response = delegate.execute(request);
            response.setRagDegraded(true);
            return response;
        }

        try {
            // 执行完整RAG管线（查询改写→混合检索→Reranking→MMR→增强提示词）
            RagResult ragResult = ragService.retrieveAndBuild(knowledgeBaseId, query, DEFAULT_TOP_K);

            // 构建引用来源列表
            List<ChatResponse.Source> sources = buildSources(ragResult.getChunks());

            // 构建增强请求
            ChatRequest enhancedRequest = buildEnhancedRequest(request, ragResult.getEnhancedPrompt());

            // 委托执行
            ChatResponse response = delegate.execute(enhancedRequest);
            response.setSources(sources);

            return response;
        } catch (Exception e) {
            // RAG检索异常时降级为无RAG模式执行
            log.error("RAG检索异常，降级为无RAG模式: {}", e.getMessage(), e);
            ChatResponse response = delegate.execute(request);
            response.setRagDegraded(true);
            response.setSources(new ArrayList<>());
            return response;
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(ChatRequest request) {
        String knowledgeBaseId = request.getKnowledgeBaseId();
        String query = request.getQuery();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();

        // 检查RAG前置条件
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank() || query == null || query.isBlank()) {
            log.warn("RAG前置条件不满足（knowledgeBaseId或query为空），跳过RAG直接执行流式");
            // 降级标志在流结束后由DONE事件携带，不提前发射DONE
            return delegate.executeStream(request)
                    .map(event -> {
                        // 在最终的DONE事件中标记降级
                        if (event.getType() == StreamEventType.DONE) {
                            event.getData().put("ragDegraded", true);
                        }
                        return event;
                    });
        }

        try {
            // 执行完整RAG管线
            RagResult ragResult = ragService.retrieveAndBuild(knowledgeBaseId, query, DEFAULT_TOP_K);

            // 构建引用来源并发射RAG_RETRIEVE事件
            List<SourceRef> sourceRefs = ragResult.getChunks().stream()
                    .map(chunk -> SourceRef.builder()
                            .docName(chunk.getDocId())
                            .chunkContent(chunk.getContent())
                            .score(chunk.getScore())
                            .build())
                    .toList();

            // 构建增强请求
            ChatRequest enhancedRequest = buildEnhancedRequest(request, ragResult.getEnhancedPrompt());

            // 先发射RAG_RETRIEVE事件，再委托流式执行
            return Flux.just(StreamEvent.ragRetrieve(sourceRefs, sessionId))
                    .concatWith(delegate.executeStream(enhancedRequest));
        } catch (Exception e) {
            // RAG检索异常时降级为无RAG模式执行
            log.error("RAG检索异常(流式)，降级为无RAG模式: {}", e.getMessage(), e);
            // 降级标志在流结束后由DONE事件携带，不提前发射DONE
            return delegate.executeStream(request)
                    .map(event -> {
                        if (event.getType() == com.ai.agent.types.enums.StreamEventType.DONE) {
                            event.getData().put("ragDegraded", true);
                        }
                        return event;
                    });
        }
    }

    /**
     * 构建增强请求 — 用增强提示词替换原始query
     */
    private ChatRequest buildEnhancedRequest(ChatRequest original, String enhancedQuery) {
        return ChatRequest.builder()
                .userId(original.getUserId())
                .sessionId(original.getSessionId())
                .query(enhancedQuery)
                .mode(original.getMode())
                .engine(original.getEngine())
                .ragEnabled(original.getRagEnabled())
                .knowledgeBaseId(original.getKnowledgeBaseId())
                .agentId(original.getAgentId())
                .enableThinking(original.getEnableThinking())
                .build();
    }

    /**
     * 将DocumentChunk列表转换为ChatResponse.Source列表
     */
    private List<ChatResponse.Source> buildSources(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(chunk -> ChatResponse.Source.builder()
                        .docName(chunk.getDocId())
                        .chunkContent(chunk.getContent())
                        .score(chunk.getScore())
                        .build())
                .toList();
    }

}
