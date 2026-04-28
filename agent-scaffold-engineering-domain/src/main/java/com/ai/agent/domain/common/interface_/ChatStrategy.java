package com.ai.agent.domain.common.interface_;

import com.ai.agent.domain.common.valobj.ChatRequest;
import com.ai.agent.domain.common.valobj.ChatResponse;
import com.ai.agent.domain.common.valobj.StreamEvent;
import reactor.core.publisher.Flux;

/**
 * 聊天策略接口 — 从 chat 上下文提取到 common
 * chat 上下文的策略和 agent 上下文的 TaskRuntime 都实现此接口
 */
public interface ChatStrategy {

    ChatResponse execute(ChatRequest request);

    Flux<StreamEvent> executeStream(ChatRequest request);
}
