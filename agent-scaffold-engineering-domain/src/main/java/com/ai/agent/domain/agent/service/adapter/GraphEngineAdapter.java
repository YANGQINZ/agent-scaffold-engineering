package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.agent.repository.ContextStore;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.agent.service.engine.ConditionEvaluator;
import com.ai.agent.domain.agent.service.engine.GraphChannel;
import com.ai.agent.domain.agent.service.tool.McpToolProvider;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.exception.AgentException;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

/**
 * Graph引擎适配器 — 封装Spring AI Alibaba StateGraph
 *
 * 职责：根据预定义的流程图(StateGraph)按顺序执行节点任务，
 * 支持条件跳转(策略链评估)、异常重试(指数退避≤3次)、检查点与断点恢复。
 * 每个节点执行前后与ContextStore交互，实现跨引擎状态共享。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphEngineAdapter implements EngineAdapter {

    private final ChatModel chatModel;
    private final AgentRegistry agentRegistry;
    private final McpToolProvider mcpToolProvider;
    private final ConditionEvaluator conditionEvaluator;

    /** 最大重试次数 */
    private static final int MAX_RETRY = 3;

    /** 指数退避基数（毫秒） */
    private static final long RETRY_BASE_MS = 1000L;

    @Override
    public EngineType getType() {
        return EngineType.GRAPH;
    }

    @Override
    public AgentMessage execute(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        log.info("GraphEngineAdapter执行: agentId={}, 节点数={}, 边数={}",
                def.getAgentId(), def.getGraphNodes().size(), def.getGraphEdges().size());

        validateGraphConfig(def);

        try {
            ctx.appendHistory(input);

            StateGraph graph = buildGraph(def, ctx);
            CompiledGraph compiled = compileGraph(graph);

            RunnableConfig config = buildRunnableConfig(ctx.getSessionId());
            Map<String, Object> graphInput = toGraphInput(input);
            Optional<OverAllState> result = compiled.invoke(graphInput, config);

            String output = result.map(s -> s.value("output", "")).orElse(input.getContent());
            AgentMessage response = toAgentMessage(output, def.getAgentId(), ctx.getSessionId());

            ctx.appendHistory(response);
            return response;

        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("GraphEngineAdapter执行失败: agentId={}, error={}",
                    def.getAgentId(), e.getMessage(), e);
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Graph编排执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        return Flux.defer(() -> {
            log.info("GraphEngineAdapter流式执行: agentId={}", def.getAgentId());

            validateGraphConfig(def);

            try {
                ctx.appendHistory(input);

                StateGraph graph = buildGraph(def, ctx);
                CompiledGraph compiled = compileGraph(graph);
                RunnableConfig config = buildRunnableConfig(ctx.getSessionId());
                Map<String, Object> graphInput = toGraphInput(input);

                String[] finalAnswer = {input.getContent()};

                return compiled.stream(graphInput, config)
                        .flatMap(nodeOutput -> {
                            String nodeName = nodeOutput.node();
                            String output = nodeOutput.state().value("output", "");
                            if (output != null && !output.isBlank()) {
                                finalAnswer[0] = output;
                            }
                            return Flux.just(
                                    StreamEvent.nodeStart(nodeName, ctx.getSessionId()),
                                    StreamEvent.nodeEnd(nodeName, ctx.getSessionId())
                            );
                        })
                        .concatWith(Flux.defer(() -> {
                            AgentMessage response = toAgentMessage(finalAnswer[0],
                                    def.getAgentId(), ctx.getSessionId());
                            ctx.appendHistory(response);
                            return Flux.just(
                                    StreamEvent.textDelta(finalAnswer[0], ctx.getSessionId()),
                                    StreamEvent.done(false, null, ctx.getSessionId())
                            );
                        }));

            } catch (Exception e) {
                log.error("GraphEngineAdapter流式执行失败: {}", e.getMessage(), e);
                return Flux.error(new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                        "Graph编排流式执行失败: " + e.getMessage(), e));
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getTypedChannel(Class<T> channelType) {
        if (channelType == GraphChannel.class) {
            return (T) new GraphChannel(this);
        }
        throw new AgentException(Constants.ErrorCode.AGENT_MODE_UNSUPPORTED,
                "不支持的通道类型: " + channelType.getSimpleName());
    }

    // ═══════════════════════════════════════════════════════
    // StateGraph 构建
    // ═══════════════════════════════════════════════════════

    /**
     * 构建 StateGraph — 添加节点、边和条件边
     */
    private StateGraph buildGraph(AgentDefinition def, ContextStore ctx) throws GraphStateException {
        StateGraph graph = new StateGraph(defaultKeyStrategyFactory(
                Map.of("decision", KeyStrategy.REPLACE)
        ));

        // 1. 添加节点（每个节点包含重试逻辑和ContextStore注入）
        for (WorkflowNode node : def.getGraphNodes()) {
            graph.addNode(node.getId(), node_async(buildNodeAction(def, node, ctx)));
        }

        // 2. 添加起始边
        graph.addEdge(START, def.getGraphStart());

        // 3. 构建边映射
        Map<String, List<GraphEdge>> edgeMap = def.getGraphEdges().stream()
                .collect(Collectors.groupingBy(GraphEdge::getFrom));

        // 4. 收集有出边的节点（用于判断叶子节点）
        Set<String> nodesWithOutEdges = def.getGraphEdges().stream()
                .map(GraphEdge::getFrom)
                .collect(Collectors.toSet());

        // 5. 添加边和条件边
        for (Map.Entry<String, List<GraphEdge>> entry : edgeMap.entrySet()) {
            String fromNode = entry.getKey();
            List<GraphEdge> outEdges = entry.getValue();

            List<GraphEdge> conditionalEdges = outEdges.stream()
                    .filter(e -> e.getCondition() != null && !e.getCondition().isBlank())
                    .toList();
            List<GraphEdge> unconditionalEdges = outEdges.stream()
                    .filter(e -> e.getCondition() == null || e.getCondition().isBlank())
                    .toList();

            // 条件边：使用 ConditionEvaluator 策略链评估
            if (!conditionalEdges.isEmpty()) {
                Map<String, String> mapping = new HashMap<>();
                for (GraphEdge edge : conditionalEdges) {
                    mapping.put(edge.getCondition(), edge.getTo());
                }
                mapping.put("__no_match__", END);

                graph.addConditionalEdges(fromNode,
                        edge_async(state -> {
                            String decision = state.value("decision", "");
                            for (GraphEdge edge : conditionalEdges) {
                                if (conditionEvaluator.evaluate(decision, edge.getCondition(), chatModel)) {
                                    log.info("Graph条件路由匹配: {} -> {} (condition={})",
                                            fromNode, edge.getTo(), edge.getCondition());
                                    return edge.getCondition();
                                }
                            }
                            log.info("Graph条件路由无匹配边，终止当前分支: nodeId={}", fromNode);
                            return "__no_match__";
                        }),
                        mapping);
            }

            // 无条件边
            for (GraphEdge edge : unconditionalEdges) {
                graph.addEdge(fromNode, edge.getTo());
            }
        }

        // 6. 叶子节点自动连到 END
        for (WorkflowNode node : def.getGraphNodes()) {
            if (!nodesWithOutEdges.contains(node.getId())) {
                graph.addEdge(node.getId(), END);
            }
        }

        return graph;
    }

    /**
     * 构建节点执行动作 — 包含重试逻辑和ContextStore注入
     */
    private NodeAction buildNodeAction(AgentDefinition def, WorkflowNode node, ContextStore ctx) {
        return state -> {
            String input = state.value("output", "");
            String nodeId = node.getId();

            log.info("Graph节点执行: nodeId={}, agentId={}", nodeId, node.getAgentId());

            // 从 ContextStore 注入对话历史到输入
            String historyText = ctx.buildHistoryText();
            if (!historyText.isBlank()) {
                input = "对话历史:\n" + historyText + "\n\n当前输入: " + input;
            }

            // 注入记忆上下文（摘要 + 语义记忆 + 近期消息）
            String memoryContext = ctx.assembleMemoryContext(input);
            if (!memoryContext.isBlank()) {
                input = "记忆上下文:\n" + memoryContext + "\n\n" + input;
            }

            // 带重试的节点执行
            String output = executeWithRetry(def, node, input, ctx, nodeId);

            Map<String, Object> result = new HashMap<>();
            result.put("output", output);
            result.put("decision", output);
            result.put("messages", "节点[" + nodeId + "]执行完成");

            // 写回 ContextStore
            AgentMessage nodeMsg = AgentMessage.builder()
                    .senderId(nodeId)
                    .content(output)
                    .timestamp(System.currentTimeMillis())
                    .metadata(Map.of("role", "assistant", "engine", EngineType.GRAPH.name()))
                    .build();
            ctx.put("node_" + nodeId + "_output", output);
            ctx.appendHistory(nodeMsg);

            return result;
        };
    }

    // ═══════════════════════════════════════════════════════
    // 异常重试机制
    // ═══════════════════════════════════════════════════════

    /**
     * 带重试的节点执行 — 指数退避 1s → 2s → 4s，最多3次
     */
    private String executeWithRetry(AgentDefinition def, WorkflowNode node,
                                     String input, ContextStore ctx, String nodeId) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                return executeNodeLogic(def, node, input);
            } catch (Exception e) {
                lastException = e;
                log.warn("Graph节点执行失败(第{}/{}次): nodeId={}, error={}",
                        attempt, MAX_RETRY, nodeId, e.getMessage());

                if (attempt < MAX_RETRY) {
                    long backoffMs = RETRY_BASE_MS * (1L << (attempt - 1));
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 重试超限：节点标记 ERROR，返回错误信息（继续流程，不抛异常）
        String errorMsg = "节点[" + nodeId + "]执行失败(重试" + MAX_RETRY + "次): "
                + (lastException != null ? lastException.getMessage() : "未知错误");
        log.error("Graph节点重试超限: nodeId={}, 返回ERROR标记", nodeId);
        return errorMsg;
    }

    /**
     * 执行节点核心逻辑 — 查找子Agent执行或直接ChatModel调用
     */
    private String executeNodeLogic(AgentDefinition def, WorkflowNode node, String input) {
        String subAgentId = node.getAgentId();
        AgentDefinition subAgentDef = agentRegistry.get(subAgentId);

        if (subAgentDef != null) {
            // 子Agent执行：构建带MCP工具的ChatModel调用
            List<ToolCallback> tools = mcpToolProvider.getGraphTools(subAgentId);
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(subAgentDef.getInstruction()),
                    new UserMessage(input)
            ));
            return chatModel.call(prompt).getResult().getOutput().getText();
        }

        // 无子Agent：使用 AgentDefinition 自身的指令 + ChatModel 直接调用
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(def.getInstruction()),
                new UserMessage(input)
        ));
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    // ═══════════════════════════════════════════════════════
    // StateGraph 公共方法
    // ═══════════════════════════════════════════════════════

    /**
     * 创建默认 KeyStrategyFactory：output替换、messages追加、decision替换
     */
    protected KeyStrategyFactory defaultKeyStrategyFactory(Map<String, KeyStrategy> extraStrategies) {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("output", KeyStrategy.REPLACE);
            strategies.put("messages", new AppendStrategy());
            strategies.putAll(extraStrategies);
            return strategies;
        };
    }

    /**
     * 编译 StateGraph（带 MemorySaver 检查点）
     */
    protected CompiledGraph compileGraph(StateGraph graph) {
        try {
            return graph.compile(
                    CompileConfig.builder()
                            .saverConfig(SaverConfig.builder().register(new MemorySaver()).build())
                            .build());
        } catch (GraphStateException e) {
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "StateGraph编译失败: " + e.getMessage(), e);
        }
    }

    protected RunnableConfig buildRunnableConfig(String sessionId) {
        return RunnableConfig.builder()
                .threadId(sessionId != null ? sessionId : UUID.randomUUID().toString())
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // 消息转换
    // ═══════════════════════════════════════════════════════

    /**
     * AgentMessage → StateGraph 输入 Map
     */
    private Map<String, Object> toGraphInput(AgentMessage msg) {
        Map<String, Object> input = new HashMap<>();
        input.put("output", msg.getContent() != null ? msg.getContent() : "");
        return input;
    }

    /**
     * StateGraph 输出 → AgentMessage
     */
    private AgentMessage toAgentMessage(String output, String agentId, String sessionId) {
        return AgentMessage.builder()
                .senderId(agentId)
                .content(output)
                .timestamp(System.currentTimeMillis())
                .metadata(Map.of(
                        "role", "assistant",
                        "engine", EngineType.GRAPH.name(),
                        "sessionId", sessionId != null ? sessionId : ""
                ))
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // 校验
    // ═══════════════════════════════════════════════════════

    private void validateGraphConfig(AgentDefinition def) {
        if (def.getGraphNodes() == null || def.getGraphNodes().isEmpty()) {
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Graph配置不完整: 缺少节点定义, agentId=" + def.getAgentId());
        }
        if (def.getGraphStart() == null || def.getGraphStart().isBlank()) {
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Graph配置不完整: 缺少起始节点, agentId=" + def.getAgentId());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 内部访问方法（供 GraphChannel 使用）
    // ═══════════════════════════════════════════════════════

    /**
     * 构建并编译 StateGraph（供 GraphChannel 获取原生对象）
     */
    public CompiledGraph buildAndCompile(AgentDefinition def, ContextStore ctx) {
        try {
            StateGraph graph = buildGraph(def, ctx);
            return compileGraph(graph);
        } catch (GraphStateException e) {
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "StateGraph编译失败: " + e.getMessage(), e);
        }
    }

}
