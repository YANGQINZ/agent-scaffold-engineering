package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import reactor.core.publisher.Flux;

/**
 * 对话策略接口
 */
public interface ChatStrategy {

    /**
     * 同步执行对话
     */
    ChatResponse execute(ChatRequest request);

    /**
     * 流式执行对话
     */
    Flux<StreamEvent> executeStream(ChatRequest request);

}
