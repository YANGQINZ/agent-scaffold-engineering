package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import reactor.core.publisher.Flux;

/**
 * RAG装饰器 — 当前仅委托给被装饰的策略
 * RAG检索增强逻辑将在Task 5中实现
 */
public class RagDecorator implements ChatStrategy {

    private final ChatStrategy delegate;

    public RagDecorator(ChatStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse execute(ChatRequest request) {
        // TODO Task 5: 在委托前执行RAG检索，将检索结果注入到request中
        return delegate.execute(request);
    }

    @Override
    public Flux<StreamEvent> executeStream(ChatRequest request) {
        // TODO Task 5: 在委托前执行RAG检索，将检索结果注入到request中
        return delegate.executeStream(request);
    }

}
