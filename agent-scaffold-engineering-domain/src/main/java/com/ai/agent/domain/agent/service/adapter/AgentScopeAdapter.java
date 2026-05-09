package com.ai.agent.domain.agent.service.adapter;

import cn.hutool.core.collection.CollectionUtil;
import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.AgentscopeAgentDefinition;
import com.ai.agent.domain.agent.model.entity.AgentscopeAgentConfig;
import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.agent.service.tool.McpToolProvider;
import com.ai.agent.domain.common.valobj.StreamEvent;
import com.ai.agent.domain.knowledge.service.rag.NodeRagService;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.exception.AgentException;
import com.ai.agent.types.exception.enums.ErrorCodeEnum;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * AgentScope引擎适配器 — 集成 spring-ai-alibaba SequentialAgent 实现 Pipeline 编排
 * 职责：处理多个Agent之间的动态协作，通过 SequentialAgent 编排子 Agent 顺序执行，
 * MCP 工具注入，统一流式输出。
 */
@Slf4j
@Service
public class AgentScopeAdapter extends AbstractEngineAdapter {

    private final AgentRegistry agentRegistry;
    private final McpToolProvider mcpToolProvider;
    private final NodeRagService nodeRagService;
    private final ChatModel chatModel;

    public AgentScopeAdapter(AgentRegistry agentRegistry,
                              McpToolProvider mcpToolProvider,
                              NodeRagService nodeRagService,
                              ChatModel chatModel) {
        super(EngineType.AGENTSCOPE);
        this.agentRegistry = agentRegistry;
        this.mcpToolProvider = mcpToolProvider;
        this.nodeRagService = nodeRagService;
        this.chatModel = chatModel;
    }

    @Override
    public AgentMessage execute(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        AgentscopeAgentDefinition asDef = (AgentscopeAgentDefinition) def;
        log.info("AgentScopeAdapter执行: agentId={}", asDef.getAgentId());

        try {
            // 1. 构建输入内容（注入记忆 + RAG 增强）
            String content = buildInputContent(input, ctx, asDef);
            boolean enableThinking = Boolean.TRUE.equals(input.getMetadataValue("enableThinking"));

            // 2. 输入历史追加
            ctx.appendHistory(input.getSenderId(), input.getContent(), input.getMetadata());

            // 3. 构建 ReactAgent 子 Agent 列表
            List<Agent> agents = buildAgents(asDef, enableThinking);

            // 4. 构建 SequentialAgent 并同步执行
            String pipelineName = (asDef.getAgentId() != null && !asDef.getAgentId().isBlank())
                    ? asDef.getAgentId() : "pipeline_" + System.currentTimeMillis();
            SequentialAgent pipeline = SequentialAgent.builder()
                    .name(pipelineName)
                    .subAgents(agents)
                    .build();

            String outputContent;
            String thinkingContent = null;

            try {
                Optional<OverAllState> result = pipeline.invoke(content);
                String lastOutputKey = resolveLastOutputKey(asDef);
                if (result.isPresent()) {
                    Optional<Object> outputOpt = result.get().value(lastOutputKey);
                    outputContent = outputOpt
                            .filter(o -> o instanceof org.springframework.ai.chat.messages.AssistantMessage)
                            .map(o -> ((org.springframework.ai.chat.messages.AssistantMessage) o).getText())
                            .orElse("");
                } else {
                    outputContent = "";
                }
            } catch (Exception e) {
                log.warn("SequentialAgent.invoke() 执行失败: {}", e.getMessage());
                outputContent = "执行失败: " + e.getMessage();
            }

            // 5. 构建最终响应
            AgentMessage response = toAgentMessage(outputContent, thinkingContent, asDef.getAgentId(), ctx.getSessionId());

            // 6. 最终响应追加历史
            ctx.appendHistory(response.getSenderId(), response.getContent(), response.getMetadata());
            return response;

        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("AgentScopeAdapter执行失败: agentId={}, error={}",
                    asDef.getAgentId(), e.getMessage(), e);
            throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                    "AgentScope编排执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        AgentscopeAgentDefinition asDef = (AgentscopeAgentDefinition) def;
        String agentId = asDef.getAgentId();

        return wrapFluxLifecycle(() -> {
            log.info("AgentScopeAdapter流式执行: agentId={}", agentId);

            try {
                // 入口处注入记忆上下文 + RAG 增强
                String content = buildInputContent(input, ctx, asDef);

                // 输入历史追加
                ctx.appendHistory(input.getSenderId(), input.getContent(), input.getMetadata());

                boolean enableThinking = Boolean.TRUE.equals(input.getMetadataValue("enableThinking"));

                // 构建 ReactAgent 子 Agent 列表
                List<Agent> agents = buildAgents(asDef, enableThinking);

                // 构建 SequentialAgent 并流式执行
                String pipelineName = (asDef.getAgentId() != null && !asDef.getAgentId().isBlank())
                        ? asDef.getAgentId() : "pipeline_" + System.currentTimeMillis();
                SequentialAgent pipeline = SequentialAgent.builder()
                        .name(pipelineName)
                        .subAgents(agents)
                        .build();

                String sessionId = ctx.getSessionId();
                String[] textAccumulator = {""};
                String[] thinkingAccumulator = {null};

                // 只有最后一个 Agent 发射 THINKING/TEXT_DELTA，中间 Agent 只发进度
                String lastAgentName = resolveLastAgentName(asDef);

                return pipeline.stream(content)
                        .flatMap(nodeOutput -> convertNodeOutput(nodeOutput, sessionId,
                                textAccumulator, thinkingAccumulator, lastAgentName))
                        .concatWith(buildDoneFlux(
                                textAccumulator[0].isEmpty() ? "（无输出）" : textAccumulator[0],
                                thinkingAccumulator[0], agentId, sessionId, ctx));
            } catch (Exception e) {
                log.error("AgentScopeAdapter流式执行失败: {}", e.getMessage(), e);
                throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                        "AgentScope流式执行失败: " + e.getMessage(), e);
            }
        }, agentId, ctx.getSessionId());
    }

    // ═══════════════════════════════════════════════════════════
    // NodeOutput → StreamEvent 转换
    // ═══════════════════════════════════════════════════════════

    /**
     * 将 SequentialAgent 流式输出的 NodeOutput 转换为前端 StreamEvent
     *
     * <p>多 Agent 场景下，只有最后一个 Agent 发射 THINKING/TEXT_DELTA，
     * 中间 Agent 只发射 NODE_START/NODE_END 进度事件，避免思考与文本交替输出。</p>
     *
     * @param nodeOutput       SequentialAgent 发射的节点输出
     * @param sessionId        会话ID
     * @param textAccumulator  文本累积器
     * @param thinkingAccumulator 思考内容累积器
     * @param lastAgentName    最后一个 Agent 的名称
     */
    private Flux<StreamEvent> convertNodeOutput(NodeOutput nodeOutput, String sessionId,
                                                 String[] textAccumulator,
                                                 String[] thinkingAccumulator,
                                                 String lastAgentName) {
        String nodeName = nodeOutput.node();
        boolean isLastAgent = nodeName.equals(lastAgentName);

        // StreamingOutput：包含流式内容（模型 token 或工具结果）
        if (nodeOutput instanceof StreamingOutput<?> streaming) {
            OutputType outputType = streaming.getOutputType();

            if (outputType == OutputType.AGENT_MODEL_STREAMING) {
                // 中间 Agent：跳过 token 级事件，不发射 THINKING/TEXT_DELTA
                if (!isLastAgent) {
                    return Flux.empty();
                }

                String chunk = streaming.chunk();
                extractThinkingFromStreaming(streaming, sessionId, thinkingAccumulator);

                // 文本输出阶段：只发射文本 delta
                if (chunk != null && !chunk.isEmpty()) {
                    textAccumulator[0] += chunk;
                    return Flux.just(StreamEvent.textDelta(chunk, sessionId));
                }

                // 思考阶段：发射增量思考内容
                var msg = streaming.message();
                if (msg != null && msg.getMetadata() != null) {
                    Object reasoning = msg.getMetadata().get("reasoningContent");
                    if (reasoning instanceof String reasoningText && !reasoningText.isEmpty()) {
                        return Flux.just(StreamEvent.thinking(reasoningText, sessionId));
                    }
                }
                return Flux.empty();
            }

            if (outputType == OutputType.AGENT_MODEL_FINISHED) {
                // 只有最后一个 Agent 累积思考内容
                if (isLastAgent) {
                    extractThinkingFromStreaming(streaming, sessionId, thinkingAccumulator);
                }
                return Flux.just(StreamEvent.nodeEnd(nodeName, sessionId));
            }

            if (outputType == OutputType.AGENT_TOOL_FINISHED
                    || outputType == OutputType.AGENT_TOOL_STREAMING) {
                return Flux.just(StreamEvent.nodeStart(nodeName, sessionId));
            }

            return Flux.empty();
        }

        // 普通 NodeOutput：节点开始/结束进度事件
        if (!nodeOutput.isEND()) {
            return Flux.just(StreamEvent.nodeStart(nodeName, sessionId));
        }
        return Flux.empty();
    }

    /**
     * 从 StreamingOutput 中提取思考内容
     *
     * <p>spring-ai-alibaba 的 StreamingOutput 通过 {@code message()} 暴露 AssistantMessage，
     * 其中 metadata 包含 "reasoningContent" 字段（当 DashScopeChatOptions.enableThinking=true 时）。</p>
     *
     * @param streaming           流式输出事件
     * @param sessionId           会话ID
     * @param thinkingAccumulator 思考内容累积器
     */
    private void extractThinkingFromStreaming(StreamingOutput<?> streaming, String sessionId,
                                               String[] thinkingAccumulator) {
        try {
            var msg = streaming.message();
            if (msg != null) {
                Map<String, Object> metadata = msg.getMetadata();
                if (metadata != null) {
                    Object reasoning = metadata.get("reasoningContent");
                    if (reasoning instanceof String reasoningText && !reasoningText.isBlank()) {
                        thinkingAccumulator[0] = (thinkingAccumulator[0] == null)
                                ? reasoningText : thinkingAccumulator[0] + reasoningText;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("从StreamingOutput提取思考内容失败: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Agent 构建
    // ═══════════════════════════════════════════════════════════

    /**
     * 根据 AgentDefinition 构建 spring-ai-alibaba ReactAgent 列表
     * 每个子 Agent 通过 outputKey 将输出存入 OverAllState，后续 Agent 通过模板变量引用
     */
    private List<Agent> buildAgents(AgentscopeAgentDefinition asDef, boolean enableThinking) {
        List<Agent> agents = new ArrayList<>();

        if (asDef.getAgentscopeAgents() != null && !asDef.getAgentscopeAgents().isEmpty()) {
            List<AgentscopeAgentConfig> configs = asDef.getAgentscopeAgents();
            for (int i = 0; i < configs.size(); i++) {
                AgentscopeAgentConfig config = configs.get(i);
                String instruction = resolveAgentInstruction(asDef, config);
                String agentName = resolveAgentName(asDef, config, i);
                String outputKey = (config.getOutputKey() != null && !config.getOutputKey().isBlank())
                        ? config.getOutputKey() : "agent_" + i;

                List<ToolCallback> tools = resolveTools(config, asDef);
                com.alibaba.cloud.ai.graph.agent.Builder agentBuilder;
                if(CollectionUtil.isEmpty(tools)){
                    agentBuilder = ReactAgent.builder()
                        .name(agentName)
                        .model(chatModel)
                        .instruction(instruction != null ? instruction : "你是一个有用的助手。")
                        .outputKey(outputKey);
                } else {
                    agentBuilder = ReactAgent.builder()
                        .name(agentName)
                        .model(chatModel)
                        .instruction(instruction != null ? instruction : "你是一个有用的助手。")
                        .tools(tools)
                        .outputKey(outputKey);
                }


                // 注入 enableThinking（通过 chatOptions）
                if (enableThinking) {
                    agentBuilder.chatOptions(
                            com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions.builder()
                                    .enableThinking(true)
                                    .build());
                }

                agents.add(agentBuilder.build());
                log.info("构建ReactAgent: index={}, agentName={}, hasTools={}, outputKey={}, enableThinking={}",
                        i, agentName, !tools.isEmpty(), outputKey, enableThinking);
            }
        } else {
            List<ToolCallback> tools = mcpToolProvider.buildGraphTools(asDef.getMcpServers());

            var agentBuilder = ReactAgent.builder()
                    .name(asDef.getName() != null ? asDef.getName() : asDef.getAgentId())
                    .model(chatModel)
                    .instruction(asDef.getInstruction() != null ? asDef.getInstruction() : "你是一个有用的助手。")
                    .tools(tools)
                    .outputKey("agent_0");

            if (enableThinking) {
                agentBuilder.chatOptions(
                        com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions.builder()
                                .enableThinking(true)
                                .build());
            }

            agents.add(agentBuilder.build());
            log.info("构建单ReactAgent: agentId={}, name={}, enableThinking={}",
                    asDef.getAgentId(), asDef.getName(), enableThinking);
        }

        return agents;
    }

    // ═══════════════════════════════════════════════════════════
    // 输入构建
    // ═══════════════════════════════════════════════════════════

    /**
     * 构建输入内容：注入记忆上下文 + RAG 增强
     */
    private String buildInputContent(AgentMessage input, ContextStore ctx, AgentscopeAgentDefinition asDef) {
        String content = input.getContent() != null ? input.getContent() : "";

        // 注入记忆上下文
        String memoryContext = ctx.assembleMemoryContext(content);
        if (!memoryContext.isBlank()) {
            content = "记忆上下文:\n" + memoryContext + "\n\n当前输入: " + content;
        }

        // RAG 增强
        WorkflowNode ragNode = WorkflowNode.builder()
                .id(asDef.getAgentId())
                .ragEnabled((Boolean) input.getMetadataValue("ragEnabled"))
                .knowledgeBaseId((String) input.getMetadataValue("knowledgeBaseId"))
                .build();
        content = nodeRagService.enhancePrompt(content, ragNode);

        return content;
    }

    // ═══════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════

    private String resolveLastOutputKey(AgentscopeAgentDefinition asDef) {
        if (asDef.getAgentscopeAgents() != null && !asDef.getAgentscopeAgents().isEmpty()) {
            int lastIdx = asDef.getAgentscopeAgents().size() - 1;
            AgentscopeAgentConfig lastConfig = asDef.getAgentscopeAgents().get(lastIdx);
            return (lastConfig.getOutputKey() != null && !lastConfig.getOutputKey().isBlank())
                    ? lastConfig.getOutputKey() : "agent_" + lastIdx;
        }
        return "agent_0";
    }

    /**
     * 解析最后一个 Agent 的名称 — 与 buildAgents() 中最后一个 Agent 的 .name() 保持一致
     */
    private String resolveLastAgentName(AgentscopeAgentDefinition asDef) {
        if (asDef.getAgentscopeAgents() != null && !asDef.getAgentscopeAgents().isEmpty()) {
            int lastIdx = asDef.getAgentscopeAgents().size() - 1;
            AgentscopeAgentConfig lastConfig = asDef.getAgentscopeAgents().get(lastIdx);
            return resolveAgentName(asDef, lastConfig, lastIdx);
        }
        // 单 Agent 场景：与 buildAgents() 中单 Agent 的 .name() 逻辑一致
        String name = asDef.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        String agentId = asDef.getAgentId();
        return (agentId != null && !agentId.isBlank()) ? agentId : "agent_0";
    }

    private String resolveAgentName(AgentscopeAgentDefinition asDef, AgentscopeAgentConfig config, int index) {
        // 优先使用 config.agentId → registry 查找 name
        if (config.getAgentId() != null && !config.getAgentId().isBlank()) {
            AgentDefinition subDef = agentRegistry.get(config.getAgentId());
            if (subDef != null && subDef.getName() != null && !subDef.getName().isBlank()) {
                return subDef.getName();
            }
            return config.getAgentId();
        }
        // 回退到 definition name，若也为空则用 agent_N
        String fallback = asDef.getName();
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return "agent_" + index;
    }

    private String resolveAgentInstruction(AgentscopeAgentDefinition asDef, AgentscopeAgentConfig config) {
        if (config.getInstruction() != null && !config.getInstruction().isBlank()) {
            return config.getInstruction();
        }
        if (config.getAgentId() != null && !config.getAgentId().isBlank()) {
            AgentDefinition subDef = agentRegistry.get(config.getAgentId());
            if (subDef != null && subDef.getInstruction() != null) {
                return subDef.getInstruction();
            }
        }
        return asDef.getInstruction() != null ? asDef.getInstruction() : "你是一个有用的助手。";
    }

    private List<ToolCallback> resolveTools(AgentscopeAgentConfig config, AgentscopeAgentDefinition asDef) {
        List<McpServerConfig> mcpServers = resolveAgentMcpServers(config, asDef);
        if (mcpServers == null || mcpServers.isEmpty()) {
            return List.of();
        }
        return mcpToolProvider.buildGraphTools(mcpServers);
    }

    private List<McpServerConfig> resolveAgentMcpServers(AgentscopeAgentConfig config, AgentscopeAgentDefinition asDef) {
        if (config.getMcpServers() != null && !config.getMcpServers().isEmpty()) {
            return config.getMcpServers();
        }
        return asDef.getMcpServers();
    }
}
