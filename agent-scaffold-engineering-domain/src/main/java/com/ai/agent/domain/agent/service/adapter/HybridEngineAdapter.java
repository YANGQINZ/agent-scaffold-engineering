package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.HybridAgentDefinition;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.agent.service.engine.ConditionEvaluator;
import com.ai.agent.domain.agent.service.tool.McpToolProvider;
import com.ai.agent.domain.common.valobj.StreamEvent;
import com.ai.agent.domain.knowledge.service.rag.NodeRagService;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.exception.AgentException;
import com.ai.agent.types.exception.enums.ErrorCodeEnum;
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
 * - 流式执行：中间节点同步执行发进度保活，叶子节点 token 级流式
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
    private final NodeRagService nodeRagService;

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
            throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                    "Hybrid编排执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        HybridAgentDefinition hyDef = (HybridAgentDefinition) def;
        return Flux.defer(() -> {
            log.info("HybridEngineAdapter流式执行: agentId={}", hyDef.getAgentId());
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

                // 识别叶子节点
                Set<String> leafIds = identifyLeafNodes(hyDef);

                if (leafIds.size() == 1) {
                    String leafId = leafIds.iterator().next();
                    WorkflowNode leafNode = hyDef.getGraphNodes().stream()
                            .filter(n -> n.getId().equals(leafId)).findFirst().orElseThrow();
                    EngineType leafEngine = resolveSubEngine(hyDef, leafNode);

                    if (leafEngine == EngineType.GRAPH) {
                        // 单叶子 GRAPH 节点：中间同步 + 叶子 token 级流式
                        return executeWithLeafStreaming(hyDef, enrichedInput, ctx, enableThinking, leafNode);
                    } else {
                        // 单叶子 AGENTSCOPE 节点：中间同步 + 叶子同步执行（agentscope 限制）
                        return executeWithAgentscopeLeaf(hyDef, enrichedInput, ctx, enableThinking, leafNode);
                    }
                } else {
                    // 多叶子节点：全部同步执行，节点级渐进输出
                    return executeWithNodeProgress(hyDef, enrichedInput, ctx, enableThinking);
                }
            } catch (Exception e) {
                log.error("HybridEngineAdapter流式执行失败: {}", e.getMessage(), e);
                return Flux.error(new AgentException(ErrorCodeEnum.AGENT_FAILED,
                        "Hybrid编排流式执行失败: " + e.getMessage(), e));
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnError(e -> {
            if (e instanceof java.io.IOException || (e.getMessage() != null
                    && e.getMessage().contains("Broken pipe"))) {
                log.warn("HybridEngineAdapter流式输出: 客户端已断开连接, agentId={}", def.getAgentId());
            }
        })
        .onErrorResume(java.io.IOException.class, e -> {
            log.warn("HybridEngineAdapter流式输出: 管道断裂，静默结束流, agentId={}", def.getAgentId());
            return Flux.empty();
        })
        .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
            log.warn("HybridEngineAdapter流式输出: 执行超时, agentId={}", def.getAgentId());
            return Flux.just(StreamEvent.done(false, Map.of("error", "timeout"), ctx.getSessionId()));
        });
    }

    // ═══════════════════════════════════════════════════════
    // 流式执行策略
    // ═══════════════════════════════════════════════════════

    /**
     * 识别叶子节点（无出边的节点）
     */
    private Set<String> identifyLeafNodes(HybridAgentDefinition hyDef) {
        Set<String> nodesWithOutEdges = hyDef.getGraphEdges().stream()
                .map(GraphEdge::getFrom).collect(Collectors.toSet());
        return hyDef.getGraphNodes().stream()
                .map(WorkflowNode::getId)
                .filter(id -> !nodesWithOutEdges.contains(id))
                .collect(Collectors.toSet());
    }

    /**
     * 解析节点的子引擎类型
     */
    private EngineType resolveSubEngine(HybridAgentDefinition hyDef, WorkflowNode node) {
        String subEngine = node.getSubEngine();
        if (subEngine != null && !subEngine.isBlank()) {
            return EngineType.valueOf(subEngine);
        }
        EngineType engineType = hyDef.getSubEngine(node.getId());
        return engineType != null ? engineType : EngineType.GRAPH;
    }

    /**
     * 单叶子 GRAPH 节点：中间节点同步执行 + 叶子节点 token 级流式
     * 复用 GraphEngineAdapter 的 streamLeafNodeTokens 方法
     */
    private Flux<StreamEvent> executeWithLeafStreaming(
            HybridAgentDefinition hyDef, String enrichedInput, ContextStore ctx,
            boolean enableThinking, WorkflowNode leafNode) throws GraphStateException {

        String sessionId = ctx.getSessionId();
        String leafNodeId = leafNode.getId();

        // 累积器
        String[] intermediateOutput = {enrichedInput};
        String[] leafFullText = {""};
        String[] thinkingContent = {null};

        // Phase 1: 构建图 — 叶子节点替换为透传 Action
        Map<String, NodeAction> overrides = Map.of(leafNodeId, state -> {
            String passthrough = (String) state.value("output").orElse("");
            Map<String, Object> result = new HashMap<>();
            result.put("output", passthrough);
            result.put("decision", passthrough);
            return result;
        });

        StateGraph graph = buildHybridGraph(hyDef, ctx, enableThinking, overrides);
        CompiledGraph compiled = graph.compile();
        RunnableConfig config = graphAdapter.buildRunnableConfig(sessionId);
        Map<String, Object> graphInput = new HashMap<>();
        graphInput.put("output", enrichedInput);

        Flux<StreamEvent> intermediateFlux = compiled.stream(graphInput, config)
                .flatMap(nodeOutput -> {
                    String nodeName = nodeOutput.node();
                    String output = (String) nodeOutput.state().value("output").orElse("");

                    if (nodeName.equals(leafNodeId)) {
                        intermediateOutput[0] = output;
                        return Flux.just(
                                StreamEvent.nodeStart(nodeName, sessionId),
                                StreamEvent.nodeEnd(nodeName, sessionId)
                        );
                    }

                    if (!output.isBlank()) {
                        intermediateOutput[0] = output;
                    }
                    nodeOutput.state().value("thinkingContent")
                            .ifPresent(tc -> thinkingContent[0] = tc.toString());
                    return Flux.just(
                            StreamEvent.nodeStart(nodeName, sessionId),
                            StreamEvent.nodeEnd(nodeName, sessionId)
                    );
                });

        // Phase 2: 叶子节点 token 级流式（复用 GraphEngineAdapter）
        Flux<StreamEvent> leafFlux = Flux.defer(() -> {
            String leafInput = nodeRagService.enhancePrompt(intermediateOutput[0], leafNode);
            return graphAdapter.streamLeafNodeTokens(leafNode, leafInput, enableThinking,
                    sessionId, leafFullText, thinkingContent);
        });

        // Phase 3: 完成阶段
        Flux<StreamEvent> doneFlux = Flux.defer(() -> {
            String finalContent = leafFullText[0].isEmpty()
                    ? intermediateOutput[0] : leafFullText[0];
            AgentMessage response = toAgentMessage(finalContent, thinkingContent[0],
                    hyDef.getAgentId(), sessionId);
            ctx.appendHistory(response.getSenderId(), response.getContent(), response.getMetadata());

            Flux<StreamEvent> thinkingFlux = Flux.empty();
            if (thinkingContent[0] != null && !thinkingContent[0].isBlank()) {
                thinkingFlux = Flux.just(StreamEvent.thinking(thinkingContent[0], sessionId));
            }
            return thinkingFlux.concatWith(Flux.just(StreamEvent.done(false, null, sessionId)));
        });

        return Flux.concat(intermediateFlux, leafFlux, doneFlux);
    }

    /**
     * 单叶子 AGENTSCOPE 节点：中间节点同步 + 叶子节点同步执行
     * agentscope-java 不支持 token 级流式，叶子节点同步执行后发 textDelta
     */
    private Flux<StreamEvent> executeWithAgentscopeLeaf(
            HybridAgentDefinition hyDef, String enrichedInput, ContextStore ctx,
            boolean enableThinking, WorkflowNode leafNode) throws GraphStateException {

        String sessionId = ctx.getSessionId();
        String leafNodeId = leafNode.getId();

        String[] intermediateOutput = {enrichedInput};
        String[] thinkingContent = {null};

        // Phase 1: 构建图 — 叶子节点替换为透传
        Map<String, NodeAction> overrides = Map.of(leafNodeId, state -> {
            String passthrough = (String) state.value("output").orElse("");
            Map<String, Object> result = new HashMap<>();
            result.put("output", passthrough);
            result.put("decision", passthrough);
            return result;
        });

        StateGraph graph = buildHybridGraph(hyDef, ctx, enableThinking, overrides);
        CompiledGraph compiled = graph.compile();
        RunnableConfig config = graphAdapter.buildRunnableConfig(sessionId);
        Map<String, Object> graphInput = new HashMap<>();
        graphInput.put("output", enrichedInput);

        Flux<StreamEvent> intermediateFlux = compiled.stream(graphInput, config)
                .flatMap(nodeOutput -> {
                    String nodeName = nodeOutput.node();
                    String output = (String) nodeOutput.state().value("output").orElse("");

                    if (!output.isBlank()) {
                        intermediateOutput[0] = output;
                    }
                    nodeOutput.state().value("thinkingContent")
                            .ifPresent(tc -> thinkingContent[0] = tc.toString());
                    return Flux.just(
                            StreamEvent.nodeStart(nodeName, sessionId),
                            StreamEvent.nodeEnd(nodeName, sessionId)
                    );
                });

        // Phase 2: 叶子节点同步执行（agentscope 限制）
        Flux<StreamEvent> leafFlux = Flux.defer(() -> {
            String leafInput = nodeRagService.enhancePrompt(intermediateOutput[0], leafNode);
            AgentMessage subInput = AgentMessage.builder()
                    .senderId("hybrid_" + leafNodeId)
                    .content(leafInput)
                    .timestamp(System.currentTimeMillis())
                    .metadata(Map.of("role", "user", "enableThinking", enableThinking))
                    .build();

            AgentDefinition subDef = findSubAgentDef(hyDef, leafNode);
            AgentMessage result = agentscopeAdapter.execute(subDef, subInput, ctx);

            Flux<StreamEvent> events = Flux.just(
                    StreamEvent.nodeStart(leafNodeId, sessionId),
                    StreamEvent.textDelta(result.getContent(), sessionId),
                    StreamEvent.nodeEnd(leafNodeId, sessionId)
            );

            if (result.getThinkingContent() != null && !result.getThinkingContent().isBlank()) {
                thinkingContent[0] = result.getThinkingContent();
            }

            AgentMessage response = toAgentMessage(result.getContent(), thinkingContent[0],
                    hyDef.getAgentId(), sessionId);
            ctx.appendHistory(response.getSenderId(), response.getContent(), response.getMetadata());

            Flux<StreamEvent> thinkingFlux = Flux.empty();
            if (thinkingContent[0] != null && !thinkingContent[0].isBlank()) {
                thinkingFlux = Flux.just(StreamEvent.thinking(thinkingContent[0], sessionId));
            }
            return events.concatWith(thinkingFlux)
                    .concatWith(Flux.just(StreamEvent.done(false, null, sessionId)));
        });

        return Flux.concat(intermediateFlux, leafFlux);
    }

    /**
     * 多叶子节点场景：全部同步执行，叶子节点发 textDelta，中间节点仅发 nodeStart/nodeEnd
     */
    private Flux<StreamEvent> executeWithNodeProgress(
            HybridAgentDefinition hyDef, String enrichedInput, ContextStore ctx,
            boolean enableThinking) throws GraphStateException {

        String sessionId = ctx.getSessionId();
        StateGraph graph = buildHybridGraph(hyDef, ctx, enableThinking);
        CompiledGraph compiled = graph.compile();
        RunnableConfig config = graphAdapter.buildRunnableConfig(sessionId);
        Map<String, Object> graphInput = new HashMap<>();
        graphInput.put("output", enrichedInput);

        String[] finalAnswer = {enrichedInput};
        String[] finalThinking = {null};

        Set<String> leafIds = identifyLeafNodes(hyDef);

        return compiled.stream(graphInput, config)
                .flatMap(nodeOutput -> {
                    String nodeName = nodeOutput.node();
                    String output = (String) nodeOutput.state().value("output").orElse("");
                    if (!output.isBlank()) {
                        finalAnswer[0] = output;
                    }
                    nodeOutput.state().value("thinkingContent")
                            .ifPresent(tc -> finalThinking[0] = tc.toString());

                    if (leafIds.contains(nodeName)) {
                        return Flux.just(
                                StreamEvent.nodeStart(nodeName, sessionId),
                                StreamEvent.textDelta(output, sessionId),
                                StreamEvent.nodeEnd(nodeName, sessionId)
                        );
                    }
                    return Flux.just(
                            StreamEvent.nodeStart(nodeName, sessionId),
                            StreamEvent.nodeEnd(nodeName, sessionId)
                    );
                })
                .concatWith(Flux.defer(() -> {
                    AgentMessage response = toAgentMessage(finalAnswer[0], finalThinking[0],
                            hyDef.getAgentId(), sessionId);
                    ctx.appendHistory(response.getSenderId(), response.getContent(), response.getMetadata());

                    Flux<StreamEvent> thinkingFlux = Flux.empty();
                    if (finalThinking[0] != null && !finalThinking[0].isBlank()) {
                        thinkingFlux = Flux.just(StreamEvent.thinking(finalThinking[0], sessionId));
                    }
                    return thinkingFlux.concatWith(Flux.just(StreamEvent.done(false, null, sessionId)));
                }));
    }

    // ═══════════════════════════════════════════════════════
    // Hybrid StateGraph 构建
    // ═══════════════════════════════════════════════════════

    private StateGraph buildHybridGraph(HybridAgentDefinition hyDef, ContextStore ctx, boolean enableThinking) throws GraphStateException {
        return buildHybridGraph(hyDef, ctx, enableThinking, Collections.emptyMap());
    }

    /**
     * 构建 Hybrid StateGraph，支持指定节点使用自定义 Action（用于流式场景下叶子节点透传）
     */
    private StateGraph buildHybridGraph(HybridAgentDefinition hyDef, ContextStore ctx,
                                         boolean enableThinking, Map<String, NodeAction> actionOverrides) throws GraphStateException {
        StateGraph graph = new StateGraph(graphAdapter.defaultKeyStrategyFactory(
                Map.of("decision", KeyStrategy.REPLACE)
        ));

        // 1. 添加节点，根据 subEngines 决定委托给哪个引擎
        for (WorkflowNode node : hyDef.getGraphNodes()) {
            // 有 override 的优先使用 override（流式场景下叶子节点透传）
            if (actionOverrides.containsKey(node.getId())) {
                graph.addNode(node.getId(), node_async(actionOverrides.get(node.getId())));
                continue;
            }

            String subEngine = node.getSubEngine();
            EngineType engineType;
            if (subEngine != null && !subEngine.isBlank()) {
                engineType = EngineType.valueOf(subEngine);
            } else {
                engineType = hyDef.getSubEngine(node.getId());
            }

            if (engineType == EngineType.AGENTSCOPE) {
                graph.addNode(node.getId(), node_async(wrapAsGraphAction(hyDef, node, ctx, enableThinking)));
            } else {
                graph.addNode(node.getId(), node_async(graphAdapter.buildNodeAction(node, enableThinking)));
            }
        }

        // 2. 添加起始边
        for (String startTarget : hyDef.getGraphStart()) {
            graph.addEdge(START, startTarget);
        }

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

            // 节点级 RAG 增强
            input = nodeRagService.enhancePrompt(input, node);

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
            throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                    "Hybrid配置不完整: 缺少节点定义, agentId=" + hyDef.getAgentId());
        }
        if (hyDef.getGraphStart() == null || hyDef.getGraphStart().isEmpty()) {
            throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                    "Hybrid配置不完整: 缺少起始节点, agentId=" + hyDef.getAgentId());
        }
    }

}
