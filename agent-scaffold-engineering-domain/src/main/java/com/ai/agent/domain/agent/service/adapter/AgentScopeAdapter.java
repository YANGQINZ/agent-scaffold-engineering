package com.ai.agent.domain.agent.service.adapter;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * AgentScope引擎适配器 — 集成 spring-ai-alibaba SequentialAgent 实现 Pipeline 编排
 * 职责：处理多个Agent之间的动态协作，通过 SequentialAgent 编排子 Agent 顺序执行，
 * MCP 工具注入，统一流式输出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentScopeAdapter implements EngineAdapter {

    private final AgentRegistry agentRegistry;
    private final McpToolProvider mcpToolProvider;
    private final NodeRagService nodeRagService;
    private final ChatModel chatModel;

    @Override
    public EngineType getType() {
        return EngineType.AGENTSCOPE;
    }

    @Override
    public AgentMessage execute(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        AgentscopeAgentDefinition asDef = (AgentscopeAgentDefinition) def;
        log.info("AgentScopeAdapter执行: agentId={}", asDef.getAgentId());

        try {
            // 1. 构建输入内容（注入记忆 + RAG 增强）
            String content = buildInputContent(input, ctx, asDef);
            boolean enableThinking = Boolean.TRUE.equals(input.getMetadataValue("enableThinking"));

            // 2. 注入输入到 ContextStore
            ctx.appendHistory(input.getSenderId(), input.getContent(), input.getMetadata());

            // 3. 构建 ReactAgent 子 Agent 列表
            List<Agent> agents = buildAgents(asDef);

            // 4. 构建 SequentialAgent 并同步执行
            SequentialAgent pipeline = SequentialAgent.builder()
                    .name(asDef.getAgentId())
                    .subAgents(agents)
                    .build();

            String outputContent;
            String thinkingContent = null;

            try {
                Optional<OverAllState> result = pipeline.invoke(content);
                // 从最后一个子 Agent 的 outputKey 提取输出
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
        return Flux.defer(() -> {
            log.info("AgentScopeAdapter流式执行: agentId={}", asDef.getAgentId());

            try {
                // 入口处注入记忆上下文 + RAG 增强
                String content = buildInputContent(input, ctx, asDef);

                // 输入历史追加
                ctx.appendHistory(input.getSenderId(), input.getContent(), input.getMetadata());

                // 构建 ReactAgent 子 Agent 列表
                List<Agent> agents = buildAgents(asDef);

                // 构建 SequentialAgent 并流式执行
                SequentialAgent pipeline = SequentialAgent.builder()
                        .name(asDef.getAgentId())
                        .subAgents(agents)
                        .build();

                String sessionId = ctx.getSessionId();
                String[] textAccumulator = {""};

                return pipeline.stream(content)
                        .flatMap(nodeOutput -> convertNodeOutput(nodeOutput, sessionId, textAccumulator))
                        .concatWith(Flux.defer(() -> {
                            // 完成阶段：历史追加 + done
                            AgentMessage response = toAgentMessage(
                                    textAccumulator[0].isEmpty() ? "（无输出）" : textAccumulator[0],
                                    null, asDef.getAgentId(), sessionId);
                            ctx.appendHistory(response.getSenderId(), response.getContent(), response.getMetadata());
                            return Flux.just(StreamEvent.done(false, null, sessionId));
                        }));
            } catch (Exception e) {
                log.error("AgentScopeAdapter流式执行失败: {}", e.getMessage(), e);
                return Flux.error(new AgentException(ErrorCodeEnum.AGENT_FAILED,
                        "AgentScope流式执行失败: " + e.getMessage(), e));
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnError(e -> {
            if (e instanceof java.io.IOException || (e.getMessage() != null
                    && e.getMessage().contains("Broken pipe"))) {
                log.warn("AgentScopeAdapter流式输出: 客户端已断开连接, agentId={}", def.getAgentId());
            }
        })
        .onErrorResume(java.io.IOException.class, e -> {
            log.warn("AgentScopeAdapter流式输出: 管道断裂，静默结束流, agentId={}", def.getAgentId());
            return Flux.empty();
        })
        .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
            log.warn("AgentScopeAdapter流式输出: 执行超时, agentId={}", def.getAgentId());
            return Flux.just(StreamEvent.done(false, Map.of("error", "timeout"), ctx.getSessionId()));
        });
    }

    // ═══════════════════════════════════════════════════════
    // NodeOutput → StreamEvent 转换
    // ═══════════════════════════════════════════════════════

    /**
     * 将 SequentialAgent 流式输出的 NodeOutput 转换为前端 StreamEvent
     *
     * @param nodeOutput SequentialAgent 发射的节点输出
     * @param sessionId 会话ID
     * @param textAccumulator 文本累积器（用于收集流式文本）
     */
    private Flux<StreamEvent> convertNodeOutput(NodeOutput nodeOutput, String sessionId,
                                                 String[] textAccumulator) {
        String nodeName = nodeOutput.node();

        // StreamingOutput：包含流式内容（模型 token 或工具结果）
        if (nodeOutput instanceof StreamingOutput<?> streaming) {
            OutputType outputType = streaming.getOutputType();

            if (outputType == OutputType.AGENT_MODEL_STREAMING) {
                // 模型 token 级流式
                String chunk = streaming.chunk();
                if (chunk != null && !chunk.isEmpty()) {
                    textAccumulator[0] += chunk;
                    return Flux.just(StreamEvent.textDelta(chunk, sessionId));
                }
                return Flux.empty();
            }

            if (outputType == OutputType.AGENT_MODEL_FINISHED) {
                // 模型输出完成，发 nodeEnd
                return Flux.just(StreamEvent.nodeEnd(nodeName, sessionId));
            }

            if (outputType == OutputType.AGENT_TOOL_FINISHED
                    || outputType == OutputType.AGENT_TOOL_STREAMING) {
                // 工具调用事件，发 nodeStart/nodeEnd 进度
                return Flux.just(StreamEvent.nodeStart(nodeName, sessionId));
            }

            // 其他类型（HOOK 等），忽略
            return Flux.empty();
        }

        // 普通 NodeOutput：节点开始/结束进度事件
        if (!nodeOutput.isEND()) {
            return Flux.just(StreamEvent.nodeStart(nodeName, sessionId));
        }
        return Flux.empty();
    }

    // ═══════════════════════════════════════════════════════
    // Agent 构建
    // ═══════════════════════════════════════════════════════

    /**
     * 根据 AgentDefinition 构建 spring-ai-alibaba ReactAgent 列表
     * 每个子 Agent 通过 outputKey 将输出存入 OverAllState，后续 Agent 通过模板变量引用
     */
    private List<Agent> buildAgents(AgentscopeAgentDefinition asDef) {
        List<Agent> agents = new ArrayList<>();

        if (asDef.getAgentscopeAgents() != null && !asDef.getAgentscopeAgents().isEmpty()) {
            List<AgentscopeAgentConfig> configs = asDef.getAgentscopeAgents();
            for (int i = 0; i < configs.size(); i++) {
                AgentscopeAgentConfig config = configs.get(i);
                String instruction = resolveAgentInstruction(asDef, config);
                String agentName = resolveAgentName(asDef, config);
                String outputKey = (config.getOutputKey() != null && !config.getOutputKey().isBlank())
                        ? config.getOutputKey() : "agent_" + i;

                // MCP 工具通过 McpToolProvider.buildGraphTools() 获取 ToolCallback
                List<ToolCallback> tools = resolveTools(config, asDef);

                ReactAgent agent = ReactAgent.builder()
                        .name(agentName)
                        .model(chatModel)
                        .instruction(instruction != null ? instruction : "你是一个有用的助手。")
                        .tools(tools)
                        .outputKey(outputKey)
                        .build();

                agents.add(agent);
                log.info("构建ReactAgent: index={}, agentName={}, hasTools={}, outputKey={}",
                        i, agentName, !tools.isEmpty(), outputKey);
            }
        } else {
            // 无子 Agent 配置时构建单个 Agent
            List<ToolCallback> tools = mcpToolProvider.buildGraphTools(asDef.getMcpServers());

            ReactAgent agent = ReactAgent.builder()
                    .name(asDef.getName() != null ? asDef.getName() : asDef.getAgentId())
                    .model(chatModel)
                    .instruction(asDef.getInstruction() != null ? asDef.getInstruction() : "你是一个有用的助手。")
                    .tools(tools)
                    .outputKey("agent_0")
                    .build();

            agents.add(agent);
            log.info("构建单ReactAgent: agentId={}, name={}", asDef.getAgentId(), asDef.getName());
        }

        return agents;
    }

    // ═══════════════════════════════════════════════════════
    // 输入构建
    // ═══════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════

    /**
     * 解析最后一个子 Agent 的 outputKey
     */
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
     * 解析子 Agent 名称
     */
    private String resolveAgentName(AgentscopeAgentDefinition asDef, AgentscopeAgentConfig config) {
        if (config.getAgentId() != null && !config.getAgentId().isBlank()) {
            AgentDefinition subDef = agentRegistry.get(config.getAgentId());
            if (subDef != null && subDef.getName() != null) {
                return subDef.getName();
            }
            return config.getAgentId();
        }
        return asDef.getName() != null ? asDef.getName() : asDef.getAgentId();
    }

    /**
     * 解析子 Agent 指令
     */
    private String resolveAgentInstruction(AgentscopeAgentDefinition asDef, AgentscopeAgentConfig config) {
        // 优先使用内联 instruction（画布 testRun 等场景直接传入）
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

    /**
     * 解析子 Agent 的 MCP 工具（转为 ToolCallback 列表）
     */
    private List<ToolCallback> resolveTools(AgentscopeAgentConfig config, AgentscopeAgentDefinition asDef) {
        List<McpServerConfig> mcpServers = resolveAgentMcpServers(config, asDef);
        if (mcpServers == null || mcpServers.isEmpty()) {
            return List.of();
        }
        return mcpToolProvider.buildGraphTools(mcpServers);
    }

    /**
     * 解析子 Agent 的 MCP 服务器配置
     */
    private List<McpServerConfig> resolveAgentMcpServers(AgentscopeAgentConfig config, AgentscopeAgentDefinition asDef) {
        if (config.getMcpServers() != null && !config.getMcpServers().isEmpty()) {
            return config.getMcpServers();
        }
        return asDef.getMcpServers();
    }

    // ═══════════════════════════════════════════════════════
    // 消息转换
    // ═══════════════════════════════════════════════════════

    private AgentMessage toAgentMessage(String output, String thinkingContent, String agentId, String sessionId) {
        return AgentMessage.builder()
                .senderId(agentId)
                .content(output)
                .thinkingContent(thinkingContent)
                .timestamp(System.currentTimeMillis())
                .metadata(Map.of(
                        "role", "assistant",
                        "engine", EngineType.AGENTSCOPE.name(),
                        "sessionId", sessionId != null ? sessionId : ""
                ))
                .build();
    }

}
