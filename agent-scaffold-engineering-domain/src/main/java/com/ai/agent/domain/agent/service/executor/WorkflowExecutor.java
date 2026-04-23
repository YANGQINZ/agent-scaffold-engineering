package com.ai.agent.domain.agent.service.executor;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.exception.AgentException;
import com.ai.agent.domain.agent.service.executor.ReactExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Workflow编排执行器 — STUB实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutor {

    private final ChatModel chatModel;
    private final ReactExecutor reactExecutor;

    /**
     * 同步执行Workflow编排
     */
    public ChatResponse execute(AgentDefinition agentDef, ChatRequest request) {
        throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED, "Workflow编排功能待实现");
    }

    /**
     * 流式执行Workflow编排
     */
    public Flux<StreamEvent> executeStream(AgentDefinition agentDef, ChatRequest request) {
        return Flux.error(new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED, "Workflow编排功能待实现"));
    }

}
