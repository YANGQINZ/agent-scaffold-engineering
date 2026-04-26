package com.ai.agent.domain.chat.service;

import com.ai.agent.domain.agent.service.ContextStoreFactory;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.domain.chat.service.strategy.ChatStrategy;
import com.ai.agent.domain.chat.service.strategy.RagDecorator;
import com.ai.agent.domain.knowledge.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 聊天门面服务 — 统一入口，路由到对应策略，按需装饰RAG
 */
@Slf4j
@Service
public class ChatFacade {

    private final ModeRouter modeRouter;
    private final RagService ragService;
    private final ContextStoreFactory contextStoreFactory;

    public ChatFacade(ModeRouter modeRouter, RagService ragService, ContextStoreFactory contextStoreFactory) {
        this.modeRouter = modeRouter;
        this.ragService = ragService;
        this.contextStoreFactory = contextStoreFactory;
    }

    /**
     * 同步对话
     */
    public ChatResponse chat(ChatRequest request) {
        ChatStrategy strategy = modeRouter.route(request.getMode());

        // 如果启用RAG，用RagDecorator包装策略（注入RagService实现RAG检索增强）
        if (Boolean.TRUE.equals(request.getRagEnabled())) {
            strategy = new RagDecorator(strategy, ragService);
        }

        return strategy.execute(request);
    }

    /**
     * 流式对话
     */
    public Flux<StreamEvent> chatStream(ChatRequest request) {
        ChatStrategy strategy = modeRouter.route(request.getMode());

        // 如果启用RAG，用RagDecorator包装策略（注入RagService实现RAG检索增强）
        if (Boolean.TRUE.equals(request.getRagEnabled())) {
            strategy = new RagDecorator(strategy, ragService);
        }

        return strategy.executeStream(request);
    }

    /**
     * 移除会话上下文（会话清理）
     *
     * @param sessionId 会话ID
     */
    public void removeSession(String sessionId) {
        contextStoreFactory.remove(sessionId);
    }

}
