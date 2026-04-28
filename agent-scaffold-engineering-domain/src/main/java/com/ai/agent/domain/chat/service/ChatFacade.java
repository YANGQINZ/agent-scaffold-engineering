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

        ChatResponse response = strategy.execute(request);

        // 发布消息创建事件 — 通知 memory 系统
        publishMessageEvent(request.getSessionId(), response.getAnswer(), "assistant");

        return response;
    }

    /**
     * 流式对话
     */
    public Flux<StreamEvent> chatStream(ChatRequest request) {
        ChatStrategy strategy = modeRouter.route(request.getMode());

        if (Boolean.TRUE.equals(request.getRagEnabled())) {
            strategy = new RagDecorator(strategy, ragService);
        }

        // 收集流式响应的完整内容
        AtomicReference<String> fullContent = new AtomicReference<>("");

        return strategy.executeStream(request)
                .doOnNext(event -> {
                    // 收集文本片段
                    if (event.getData() != null && event.getData().containsKey("text")) {
                        fullContent.updateAndGet(current -> current + event.getData().get("text"));
                    }
                })
                .doOnComplete(() -> {
                    // 流完成后发布事件
                    publishMessageEvent(request.getSessionId(), fullContent.get(), "assistant");
                });
    }

    /**
     * 移除会话上下文（会话清理）
     */
    public void removeSession(String sessionId) {
        contextStoreFactory.remove(sessionId);
    }

    private void publishMessageEvent(String sessionId, String content, String role) {
        try {
            eventPublisher.publishEvent(
                    new MessageCreatedEvent(sessionId, content, role, Instant.now())
            );
        } catch (Exception e) {
            log.warn("消息事件发布降级: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }
}
