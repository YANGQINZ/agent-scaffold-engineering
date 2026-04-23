package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Agent编排策略 — STUB实现
 * 将在Task 4中完整实现
 */
@Slf4j
@Service
public class AgentOrchestratorStrategy implements ChatStrategy {

    @Override
    public ChatResponse execute(ChatRequest request) {
        throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED, "Agent编排功能待实现");
    }

    @Override
    public Flux<StreamEvent> executeStream(ChatRequest request) {
        return Flux.error(new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED, "Agent编排功能待实现"));
    }

}
