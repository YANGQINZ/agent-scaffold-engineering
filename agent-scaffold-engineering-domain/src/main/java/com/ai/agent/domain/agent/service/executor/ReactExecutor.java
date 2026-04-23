package com.ai.agent.domain.agent.service.executor;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.exception.AgentException;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * ReAct模式执行器 — 基于ReactAgent实现推理+行动循环
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReactExecutor {

    private final ChatModel chatModel;

    /**
     * 同步执行ReAct Agent
     *
     * @param agentDef Agent定义
     * @param request  对话请求
     * @return 对话响应
     */
    public ChatResponse execute(AgentDefinition agentDef, ChatRequest request) {
        try {
            log.info("ReactExecutor执行: agentId={}, query={}", agentDef.getAgentId(), request.getQuery());

            ReactAgent agent = ReactAgent.builder()
                    .name(agentDef.getName())
                    .model(chatModel)
                    .instruction(agentDef.getInstruction())
                    .saver(new MemorySaver())
                    .enableLogging(true)
                    .build();

            AssistantMessage response = agent.call(request.getQuery());
            String answer = response.getText();

            return ChatResponse.builder()
                    .answer(answer)
                    .sessionId(request.getSessionId())
                    .build();
        } catch (Exception e) {
            log.error("ReactExecutor执行失败: agentId={}, error={}", agentDef.getAgentId(), e.getMessage(), e);
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "ReAct Agent执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式执行ReAct Agent（当前为同步转Flux实现）
     *
     * @param agentDef Agent定义
     * @param request  对话请求
     * @return 流式事件
     */
    public Flux<StreamEvent> executeStream(AgentDefinition agentDef, ChatRequest request) {
        return Flux.defer(() -> {
            try {
                ChatResponse response = execute(agentDef, request);
                return Flux.just(
                        StreamEvent.textDelta(response.getAnswer(), response.getSessionId()),
                        StreamEvent.done(false, null, response.getSessionId())
                );
            } catch (Exception e) {
                return Flux.error(e);
            }
        });
    }

}
