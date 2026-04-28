package com.ai.agent.domain.chat.service;

import com.ai.agent.domain.agent.service.ContextStoreFactory;
import com.ai.agent.domain.common.event.MessageCreatedEvent;
import com.ai.agent.domain.common.interface_.ChatStrategy;
import com.ai.agent.domain.common.valobj.ChatRequest;
import com.ai.agent.domain.common.valobj.ChatResponse;
import com.ai.agent.domain.common.valobj.StreamEvent;
import com.ai.agent.domain.chat.service.strategy.RagDecorator;
import com.ai.agent.domain.knowledge.service.rag.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天门面服务 — 统一入口，路由到对应策略，按需装饰RAG
 */
@Slf4j
@Service
public class ChatFacade {

    private final ModeRouter modeRouter;
    private final RagService ragService;
    private final ContextStoreFactory contextStoreFactory;
    private final ApplicationEventPublisher eventPublisher;

    public ChatFacade(ModeRouter modeRouter, RagService ragService,
                      ContextStoreFactory contextStoreFactory,
                      ApplicationEventPublisher eventPublisher) {
        this.modeRouter = modeRouter;
        this.ragService = ragService;
        this.contextStoreFactory = contextStoreFactory;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 同步对话
     */
    public ChatResponse chat(ChatRequest request) {
        ChatStrategy strategy = modeRouter.route(request.getMode());

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

        if (Boolean.TRUE.equals(request.getRagEnabled())) {
            strategy = new RagDecorator(strategy, ragService);
        }

        return strategy.executeStream(request);
    }

    /**
     * 移除会话上下文（会话清理）
     */
    public void removeSession(String sessionId) {
        contextStoreFactory.remove(sessionId);
    }
}
