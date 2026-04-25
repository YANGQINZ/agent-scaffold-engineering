package com.ai.agent.domain.agent.service.executor;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.entity.ToolConfig;
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
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

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

            // 从AgentDefinition.getTools()获取工具列表，创建ToolCallback并绑定
            List<ToolCallback> toolCallbacks = createToolCallbacks(agentDef.getTools());

            ReactAgent agent = ReactAgent.builder()
                    .name(agentDef.getName())
                    .model(chatModel)
                    .instruction(agentDef.getInstruction())
                    .saver(new MemorySaver())
                    .enableLogging(true)
                    .tools(toolCallbacks.toArray(new ToolCallback[0]))
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

    /**
     * 根据工具配置列表创建ToolCallback数组
     */
    @SuppressWarnings("unchecked")
    public List<ToolCallback> createToolCallbacks(List<ToolConfig> toolConfigs) {
        List<ToolCallback> callbacks = new ArrayList<>();
        if (toolConfigs == null || toolConfigs.isEmpty()) {
            return callbacks;
        }

        for (ToolConfig toolConfig : toolConfigs) {
            if (!"FUNCTION".equals(toolConfig.getType())) {
                continue;
            }
            if (toolConfig.getClassName() == null || toolConfig.getClassName().isBlank()) {
                continue;
            }
            try {
                // 通过反射实例化工具类
                Class<?> toolClass = Class.forName(toolConfig.getClassName());
                Object toolInstance = toolClass.getDeclaredConstructor().newInstance();

                if (toolInstance instanceof BiFunction<?, ?, ?> function) {
                    BiFunction<String, ToolContext, String> typedFunction =
                            (BiFunction<String, ToolContext, String>) function;
                    ToolCallback toolCallback = FunctionToolCallback.builder(toolConfig.getName(), typedFunction)
                            .description(toolConfig.getDescription())
                            .inputType(String.class)
                            .build();
                    callbacks.add(toolCallback);
                    log.info("工具绑定成功: name={}, className={}", toolConfig.getName(), toolConfig.getClassName());
                }
            } catch (Exception e) {
                log.error("工具实例化失败: name={}, className={}, error={}",
                        toolConfig.getName(), toolConfig.getClassName(), e.getMessage());
            }
        }
        return callbacks;
    }

}
