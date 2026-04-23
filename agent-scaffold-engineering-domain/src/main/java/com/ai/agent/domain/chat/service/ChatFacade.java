package com.ai.agent.domain.chat.service;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.domain.chat.service.strategy.ChatStrategy;
import com.ai.agent.domain.chat.service.strategy.RagDecorator;
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

    public ChatFacade(ModeRouter modeRouter) {
        this.modeRouter = modeRouter;
    }

    /**
     * 同步对话
     */
    public ChatResponse chat(ChatRequest request) {
        ChatStrategy strategy = modeRouter.route(request.getMode());

        // 如果启用RAG，用RagDecorator包装策略
        if (Boolean.TRUE.equals(request.getRagEnabled())) {
            strategy = new RagDecorator(strategy);
        }

        return strategy.execute(request);
    }

    /**
     * 流式对话
     */
    public Flux<StreamEvent> chatStream(ChatRequest request) {
        ChatStrategy strategy = modeRouter.route(request.getMode());

        // 如果启用RAG，用RagDecorator包装策略
        if (Boolean.TRUE.equals(request.getRagEnabled())) {
            strategy = new RagDecorator(strategy);
        }

        return strategy.executeStream(request);
    }

}
