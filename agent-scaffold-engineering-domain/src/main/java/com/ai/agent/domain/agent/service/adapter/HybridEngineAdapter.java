package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.HybridAgentDefinition;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.agent.service.engine.AgentScopeChannel;
import com.ai.agent.domain.agent.service.engine.ConditionEvaluator;
import com.ai.agent.domain.agent.service.engine.GraphChannel;
import com.ai.agent.domain.agent.service.engine.HybridChannel;
import com.ai.agent.domain.agent.service.tool.McpToolProvider;
import com.ai.agent.domain.common.valobj.StreamEvent;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.exception.AgentException;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

/**
 * Hybrid混合引擎适配器 — 外层Graph编排 + 子节点引擎委托
 * 设计思路：
 * - 外层使用 StateGraph 编排流程骨架
 * - 子节点根据 subEngines 映射委托给 GraphEngineAdapter 或 AgentScopeAdapter
 * - 记忆上下文在入口处注入1次，最终响应追加历史1次
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
    private final ConditionEvaluator conditionEvaluator;

    @Override
    public EngineType getType() {
        return EngineType.HYBRID;
    }

    @Override
    public AgentMessage execute(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        HybridAgentDefinition hyDef = (HybridAgentDefinition) def;
        log.info("HybridEngineAdapter执行: agentId={}", hyDef.getAgentId());

        validateHybridConfig(hyDef);

        try {
            // 入口处注入记忆上下文（1次）
            String enrichedInput = input.getContent() != null ? input.getContent() : "";
            String memoryContext = ctx.assembleMemoryContext(enrichedInput);
            if (!memoryContext.isBlank()) {
                enrichedInput = "记忆上下文:\n" + memoryContext + "\n\n当前输入: " + enrichedInput;
            }
            ctx.appendHistory(input.getSenderId(), input.getContent(), input.getMetadata());
            boolean enableThinking = Boolean.TRUE.equals(input.getMetadataValue("enableThinking"));

            StateGraph graph = buildHybridGraph(hyDef, ctx, enableThinking);
            CompiledGraph compiled = graph.compile();

            RunnableConfig config = graphAdapter.buildRunnableConfig(ctx.getSessionId());
            Map<String, Object> graphInput = new HashMap<>();
            graphInput.put("output", enrichedInput);
            Optional<OverAllState> result = compiled.invoke(graphInput, config);

            String output = result.map(s -> (String) s.value("output").orElse("")).orElse(enrichedInput);

            // 从 StateGraph state 中提取最终节点的思考内容
            String thinkingContent = result.map(s -> {
                Object tc = s.value("thinkingContent").orElse(null);
                return tc != null ? tc.toString() : null;
            }).orElse(null);

            AgentMessage response = toAgentMessage(output, thinkingContent, hyDef.getAgentId(), ctx.getSessionId());

            // 最终响应追加历史（1次）
            ctx.appendHistory(response.getSenderId(), response.getContent(), response.getMetadata());
            return response;

        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("HybridEngineAdapter执行失败: agentId={}, error={}",
                    hyDef.getAgentId(), e.getMessage(), e);
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Hybrid编排执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        return Flux.defer(() -> {
            try {
                AgentMessage result = execute(def, input, ctx);
                String sessionId = ctx.getSessionId();

                Flux<StreamEvent> events = Flux.empty();

                // 先发射思考过程
                if (result.getThinkingContent() != null && !result.getThinkingContent().isBlank()) {
                    events = events.concatWith(Flux.just(
                            StreamEvent.thinking(result.getThinkingContent(), sessionId)));
                }

                // 再发射文本内容
                return events.concatWith(Flux.just(
                        StreamEvent.textDelta(result.getContent(), sessionId),
                        StreamEvent.done(false, null, sessionId)
                ));
            } catch (Exception e) {
                return Flux.error(new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                        "Hybrid流式执行失败: " + e.getMessage(), e));
            }
        }).subscribeOn(Schedulers.boundedElastic()); // 避免阻塞WebFlux事件循环
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

    private StateGraph buildHybridGraph(HybridAgentDefinition hyDef, ContextStore ctx, boolean enableThinking) throws GraphStateException {
        StateGraph graph = new StateGraph(graphAdapter.defaultKeyStrategyFactory(
                Map.of("decision", KeyStrategy.REPLACE)
        ));

        // 1. 添加节点，根据 subEngines 决定委托给哪个引擎
        for (WorkflowNode node : hyDef.getGraphNodes()) {
            EngineType subEngine = hyDef.getSubEngine(node.getId());

            if (subEngine == EngineType.AGENTSCOPE) {
                graph.addNode(node.getId(), node_async(wrapAsGraphAction(hyDef, node, ctx, enableThinking)));
            } else {
                // 复用 GraphEngineAdapter 的节点构建方法
                graph.addNode(node.getId(), node_async(graphAdapter.buildNodeAction(hyDef, node, ctx, enableThinking)));
            }
        }

        // 2. 添加起始边
        graph.addEdge(START, hyDef.getGraphStart());

        // 3. 构建边映射
        Map<String, List<GraphEdge>> edgeMap = hyDef.getGraphEdges().stream()
                .collect(Collectors.groupingBy(GraphEdge::getFrom));

        Set<String> nodesWithOutEdges = hyDef.getGraphEdges().stream()
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
                mapping.put("__default__", END);

                graph.addConditionalEdges(fromNode,
                        edge_async(state -> {
                            String decision = (String) state.value("decision").orElse("");
                            for (GraphEdge edge : conditionalEdges) {
                                if (conditionEvaluator.evaluate(decision, edge.getCondition(), chatModel)) {
                                    return edge.getCondition();
                                }
                            }
                            return "__default__";
                        }),
                        mapping);
            }

            for (GraphEdge edge : unconditionalEdges) {
                graph.addEdge(fromNode, edge.getTo());
            }
        }

        // 5. 叶子节点自动连到 END
        for (WorkflowNode node : hyDef.getGraphNodes()) {
            if (!nodesWithOutEdges.contains(node.getId())) {
                graph.addEdge(node.getId(), END);
            }
        }

        return graph;
    }

    /**
     * 将 AgentScope 执行包装为 StateGraph 的 NodeAction
     */
    private NodeAction wrapAsGraphAction(HybridAgentDefinition hyDef, WorkflowNode node, ContextStore ctx, boolean enableThinking) {
        return state -> {
            String input = (String) state.value("output").orElse("");
            String nodeId = node.getId();

            log.info("Hybrid子节点委托AgentScope: nodeId={}", nodeId);

            // 构建子 Agent 输入
            AgentMessage subInput = AgentMessage.builder()
                    .senderId("hybrid_" + nodeId)
                    .content(input)
                    .timestamp(System.currentTimeMillis())
                    .metadata(Map.of("role", "user", "enableThinking", enableThinking))
                    .build();

            // 委托 AgentScopeAdapter 执行
            AgentDefinition subDef = findSubAgentDef(hyDef, node);
            AgentMessage result = agentscopeAdapter.execute(subDef, subInput, ctx);

            // 写回 state
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("output", result.getContent());
            resultMap.put("decision", result.getContent());
            return resultMap;
        };
    }

    /**
     * 查找子 Agent 定义
     */
    private AgentDefinition findSubAgentDef(HybridAgentDefinition hyDef, WorkflowNode node) {
        if (node.getAgentId() != null && !node.getAgentId().isBlank()) {
            AgentDefinition subDef = agentRegistry.get(node.getAgentId());
            return subDef != null ? subDef : hyDef;
        }
        return hyDef;
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
                        "engine", EngineType.HYBRID.name(),
                        "sessionId", sessionId != null ? sessionId : ""
                ))
                .build();
    }

    private void validateHybridConfig(HybridAgentDefinition hyDef) {
        if (hyDef.getGraphNodes() == null || hyDef.getGraphNodes().isEmpty()) {
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Hybrid配置不完整: 缺少节点定义, agentId=" + hyDef.getAgentId());
        }
        if (hyDef.getGraphStart() == null || hyDef.getGraphStart().isBlank()) {
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Hybrid配置不完整: 缺少起始节点, agentId=" + hyDef.getAgentId());
        }
    }

}
