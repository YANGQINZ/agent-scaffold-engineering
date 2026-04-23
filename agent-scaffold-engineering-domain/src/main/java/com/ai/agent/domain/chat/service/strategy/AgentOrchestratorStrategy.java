package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.agent.service.executor.GraphExecutor;
import com.ai.agent.domain.agent.service.executor.HybridExecutor;
import com.ai.agent.domain.agent.service.executor.ReactExecutor;
import com.ai.agent.domain.agent.service.executor.WorkflowExecutor;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.enums.AgentMode;
import com.ai.agent.types.exception.AgentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Agent编排策略 — 根据AgentMode路由到对应执行器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestratorStrategy implements ChatStrategy {

    private final AgentRegistry agentRegistry;
    private final ReactExecutor reactExecutor;
    private final WorkflowExecutor workflowExecutor;
    private final GraphExecutor graphExecutor;
    private final HybridExecutor hybridExecutor;

    private final Map<AgentMode, BiFunction<AgentDefinition, ChatRequest, ChatResponse>> executorMap = new EnumMap<>(AgentMode.class);

    /**
     * 初始化执行器路由映射
     */
    private Map<AgentMode, BiFunction<AgentDefinition, ChatRequest, ChatResponse>> getExecutorMap() {
        if (executorMap.isEmpty()) {
            executorMap.put(AgentMode.REACT, reactExecutor::execute);
            executorMap.put(AgentMode.WORKFLOW, workflowExecutor::execute);
            executorMap.put(AgentMode.GRAPH, graphExecutor::execute);
            executorMap.put(AgentMode.HYBRID, hybridExecutor::execute);
        }
        return executorMap;
    }

    @Override
    public ChatResponse execute(ChatRequest request) {
        AgentMode agentMode = request.getAgentMode();
        if (agentMode == null) {
            log.error("Agent模式为空");
            throw new AgentException(Constants.ErrorCode.AGENT_MODE_UNSUPPORTED, "Agent模式不能为空");
        }

        AgentDefinition agentDef = resolveAgentDefinition(request);

        log.info("AgentOrchestrator路由: agentMode={}, agentId={}", agentMode, agentDef.getAgentId());

        BiFunction<AgentDefinition, ChatRequest, ChatResponse> executor = getExecutorMap().get(agentMode);
        if (executor == null) {
            log.error("不支持的Agent模式: {}", agentMode);
            throw new AgentException(Constants.ErrorCode.AGENT_MODE_UNSUPPORTED,
                    "不支持的Agent模式: " + agentMode.getDescription());
        }

        return executor.apply(agentDef, request);
    }

    @Override
    public Flux<StreamEvent> executeStream(ChatRequest request) {
        // 当前为同步转Flux实现，后续各执行器将原生支持流式
        return Flux.defer(() -> {
            try {
                ChatResponse response = execute(request);
                return Flux.just(
                        StreamEvent.textDelta(response.getAnswer(), response.getSessionId()),
                        StreamEvent.done(false, null, response.getSessionId())
                );
            } catch (Exception e) {
                return Flux.error(e);
            }
        });
    }

    /**
     * 解析Agent定义 — 从注册中心获取或创建默认定义
     */
    private AgentDefinition resolveAgentDefinition(ChatRequest request) {
        // 当前从注册中心查找，未找到则构建默认Agent定义
        AgentDefinition agentDef = agentRegistry.get(request.getAgentMode().name());
        if (agentDef == null) {
            log.info("未找到Agent定义: agentId={}，创建默认定义", request.getAgentMode().name());
            agentDef = AgentDefinition.builder()
                    .agentId(request.getAgentMode().name())
                    .name("default-" + request.getAgentMode().name().toLowerCase() + "-agent")
                    .mode(request.getAgentMode())
                    .instruction("你是一个智能助手，请根据用户的问题给出准确的回答。")
                    .modelConfig(new ModelConfig())
                    .build();
        }
        return agentDef;
    }

}
