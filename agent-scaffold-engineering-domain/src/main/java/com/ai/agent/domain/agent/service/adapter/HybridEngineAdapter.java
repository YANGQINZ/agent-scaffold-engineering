package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.SessionContext;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.agent.repository.ContextStore;
import com.ai.agent.domain.agent.service.engine.AgentScopeChannel;
import com.ai.agent.domain.agent.service.engine.GraphChannel;
import com.ai.agent.domain.agent.service.engine.HybridChannel;
import com.ai.agent.domain.agent.service.AgentRegistry;
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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

/**
 * Hybrid混合引擎适配器 — 外层Graph编排 + 子节点引擎委托
 *
 * 设计思路：
 * - 外层使用 StateGraph 编排流程骨架
 * - 子节点根据 subEngines 映射委托给 GraphEngineAdapter 或 AgentScopeAdapter
 * - try-finally 保证 switchEngine 安全（即使子节点异常也能切回引擎状态）
 * - HybridChannel 双通道同时暴露 GraphChannel 和 AgentScopeChannel
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridEngineAdapter implements EngineAdapter {

    private final GraphEngineAdapter graphAdapter;
    private final AgentScopeAdapter agentscopeAdapter;
    private final ChatModel chatModel;
    private final AgentRegistry agentRegistry;
    private final McpToolProvider mcpToolProvider;
    private final com.ai.agent.domain.agent.service.engine.ConditionEvaluator conditionEvaluator;

    @Override
    public EngineType getType() {
        return EngineType.HYBRID;
    }

    @Override
    public AgentMessage execute(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        log.info("HybridEngineAdapter执行: agentId={}", def.getAgentId());

        validateHybridConfig(def);

        try {
            ctx.appendHistory(input);

            // 构建外层 StateGraph，子节点根据 subEngines 委托不同引擎
            StateGraph graph = buildHybridGraph(def, ctx);
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
            log.error("HybridEngineAdapter执行失败: agentId={}, error={}",
                    def.getAgentId(), e.getMessage(), e);
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Hybrid编排执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        return Flux.defer(() -> {
            try {
                AgentMessage result = execute(def, input, ctx);
                return Flux.just(
                        StreamEvent.textDelta(result.getContent(), ctx.getSessionId()),
                        StreamEvent.done(false, null, ctx.getSessionId())
                );
            } catch (Exception e) {
                return Flux.error(new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                        "Hybrid流式执行失败: " + e.getMessage(), e));
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getTypedChannel(Class<T> channelType) {
        if (channelType == HybridChannel.class) {
            return (T) new HybridChannel(
                    graphAdapter.getTypedChannel(GraphChannel.class),
                    agentscopeAdapter.getTypedChannel(AgentScopeChannel.class)
            );
        }
        throw new AgentException(Constants.ErrorCode.AGENT_MODE_UNSUPPORTED,
                "不支持的通道类型: " + channelType.getSimpleName());
    }

    // ═══════════════════════════════════════════════════════
    // Hybrid StateGraph 构建
    // ═══════════════════════════════════════════════════════

    /**
     * 构建混合模式 StateGraph — 子节点根据 subEngines 映射委托不同引擎
     */
    private StateGraph buildHybridGraph(AgentDefinition def, ContextStore ctx) throws GraphStateException {
        StateGraph graph = new StateGraph(defaultKeyStrategyFactory(
                Map.of("decision", KeyStrategy.REPLACE)
        ));

        // 1. 添加节点，根据 subEngines 决定委托给哪个引擎
        for (WorkflowNode node : def.getGraphNodes()) {
            EngineType subEngine = def.getSubEngine(node.getId());

            if (subEngine == EngineType.AGENTSCOPE) {
                // 包装为 StateGraph 节点，内部走 AgentScope
                graph.addNode(node.getId(), node_async(wrapAsGraphAction(def, node, ctx)));
            } else {
                // 默认走 Graph 单节点 (ChatModel)
                graph.addNode(node.getId(), node_async(buildGraphNodeAction(def, node, ctx)));
            }
        }

        // 2. 添加起始边
        graph.addEdge(START, def.getGraphStart());

        // 3. 构建边映射
        Map<String, List<GraphEdge>> edgeMap = def.getGraphEdges().stream()
                .collect(Collectors.groupingBy(GraphEdge::getFrom));

        Set<String> nodesWithOutEdges = def.getGraphEdges().stream()
                .map(GraphEdge::getFrom)
                .collect(Collectors.toSet());

        // 4. 添加边和条件边
        for (Map.Entry<String, List<GraphEdge>> entry : edgeMap.entrySet()) {
            String fromNode = entry.getKey();
            List<GraphEdge> outEdges = entry.getValue();

            List<GraphEdge> conditionalEdges = outEdges.stream()
                    .filter(e -> e.getCondition() != null && !e.getCondition().isBlank())
                    .toList();
            List<GraphEdge> unconditionalEdges = outEdges.stream()
                    .filter(e -> e.getCondition() == null || e.getCondition().isBlank())
                    .toList();

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
                                    return edge.getCondition();
                                }
                            }
                            return "__no_match__";
                        }),
                        mapping);
            }

            for (GraphEdge edge : unconditionalEdges) {
                graph.addEdge(fromNode, edge.getTo());
            }
        }

        // 5. 叶子节点自动连到 END
        for (WorkflowNode node : def.getGraphNodes()) {
            if (!nodesWithOutEdges.contains(node.getId())) {
                graph.addEdge(node.getId(), END);
            }
        }

        return graph;
    }

    /**
     * 将 AgentScope 执行包装为 StateGraph 的 NodeAction
     *
     * try-finally 保证 switchEngine 安全
     */
    private NodeAction wrapAsGraphAction(AgentDefinition def, WorkflowNode node, ContextStore ctx) {
        return state -> {
            String input = state.value("output", "");
            String nodeId = node.getId();

            log.info("Hybrid子节点委托AgentScope: nodeId={}", nodeId);

            try {
                // 切换引擎到 AGENTSCOPE
                if (ctx instanceof SessionContext sc) {
                    sc.switchEngine(EngineType.AGENTSCOPE);
                }

                // 构建子 Agent 输入
                AgentMessage subInput = AgentMessage.builder()
                        .senderId("hybrid_" + nodeId)
                        .content(input)
                        .timestamp(System.currentTimeMillis())
                        .metadata(Map.of("role", "user"))
                        .build();

                // 委托 AgentScopeAdapter 执行
                AgentDefinition subDef = findSubAgentDef(def, node);
                AgentMessage result = agentscopeAdapter.execute(subDef, subInput, ctx);

                // 写回 state
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("output", result.getContent());
                resultMap.put("decision", result.getContent());
                return resultMap;

            } finally {
                // 确保切回引擎（即使子节点异常）
                if (ctx instanceof SessionContext sc) {
                    sc.switchEngine(EngineType.GRAPH);
                }
            }
        };
    }

    /**
     * 构建 Graph 单节点执行动作（默认引擎）
     */
    private NodeAction buildGraphNodeAction(AgentDefinition def, WorkflowNode node, ContextStore ctx) {
        return state -> {
            String input = state.value("output", "");
            String nodeId = node.getId();

            log.info("Hybrid子节点Graph执行: nodeId={}", nodeId);

            String historyText = ctx.buildHistoryText();
            if (!historyText.isBlank()) {
                input = "对话历史:\n" + historyText + "\n\n当前输入: " + input;
            }

            // 注入记忆上下文（摘要 + 语义记忆 + 近期消息）
            String memoryContext = ctx.assembleMemoryContext(input);
            if (!memoryContext.isBlank()) {
                input = "记忆上下文:\n" + memoryContext + "\n\n" + input;
            }

            AgentDefinition subDef = findSubAgentDef(def, node);
            String instruction = subDef != null ? subDef.getInstruction() : def.getInstruction();

            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(instruction),
                    new UserMessage(input)
            ));
            String output = chatModel.call(prompt).getResult().getOutput().getText();

            AgentMessage nodeMsg = AgentMessage.builder()
                    .senderId(nodeId)
                    .content(output)
                    .timestamp(System.currentTimeMillis())
                    .metadata(Map.of("role", "assistant", "engine", EngineType.HYBRID.name()))
                    .build();
            ctx.appendHistory(nodeMsg);

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("output", output);
            resultMap.put("decision", output);
            return resultMap;
        };
    }

    /**
     * 查找子 Agent 定义
     */
    private AgentDefinition findSubAgentDef(AgentDefinition def, WorkflowNode node) {
        if (node.getAgentId() != null && !node.getAgentId().isBlank()) {
            AgentDefinition subDef = agentRegistry.get(node.getAgentId());
            return subDef != null ? subDef : def;
        }
        return def;
    }

    // ═══════════════════════════════════════════════════════
    // 公共方法
    // ═══════════════════════════════════════════════════════

    protected KeyStrategyFactory defaultKeyStrategyFactory(Map<String, KeyStrategy> extraStrategies) {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("output", KeyStrategy.REPLACE);
            strategies.put("messages", new AppendStrategy());
            strategies.putAll(extraStrategies);
            return strategies;
        };
    }

    protected CompiledGraph compileGraph(StateGraph graph) {
        try {
            return graph.compile(
                    CompileConfig.builder()
                            .saverConfig(SaverConfig.builder().register(new MemorySaver()).build())
                            .build());
        } catch (GraphStateException e) {
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Hybrid StateGraph编译失败: " + e.getMessage(), e);
        }
    }

    protected RunnableConfig buildRunnableConfig(String sessionId) {
        return RunnableConfig.builder()
                .threadId(sessionId != null ? sessionId : UUID.randomUUID().toString())
                .build();
    }

    private Map<String, Object> toGraphInput(AgentMessage msg) {
        Map<String, Object> input = new HashMap<>();
        input.put("output", msg.getContent() != null ? msg.getContent() : "");
        return input;
    }

    private AgentMessage toAgentMessage(String output, String agentId, String sessionId) {
        return AgentMessage.builder()
                .senderId(agentId)
                .content(output)
                .timestamp(System.currentTimeMillis())
                .metadata(Map.of(
                        "role", "assistant",
                        "engine", EngineType.HYBRID.name(),
                        "sessionId", sessionId != null ? sessionId : ""
                ))
                .build();
    }

    private void validateHybridConfig(AgentDefinition def) {
        if (def.getGraphNodes() == null || def.getGraphNodes().isEmpty()) {
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Hybrid配置不完整: 缺少节点定义, agentId=" + def.getAgentId());
        }
        if (def.getGraphStart() == null || def.getGraphStart().isBlank()) {
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Hybrid配置不完整: 缺少起始节点, agentId=" + def.getAgentId());
        }
    }

}
