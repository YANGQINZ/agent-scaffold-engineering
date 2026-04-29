package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.GraphAgentDefinition;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.agent.service.engine.ConditionEvaluator;
import com.ai.agent.domain.common.valobj.StreamEvent;
import com.ai.agent.domain.common.valobj.ThinkingExtractor;
import com.ai.agent.types.exception.enums.ErrorCodeEnum;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.exception.AgentException;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.NodeAction;
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
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

/**
 * Graph引擎适配器 — 封装Spring AI Alibaba StateGraph
 * 职责：根据预定义的流程图(StateGraph)按顺序执行节点任务，
 * 支持条件跳转(策略链评估)、异常重试(指数退避≤3次)。
 * 记忆上下文在入口处注入1次，最终响应追加历史1次，中间节点不写历史。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphEngineAdapter implements EngineAdapter {

    private final ChatModel chatModel;
    private final AgentRegistry agentRegistry;
    private final ConditionEvaluator conditionEvaluator;

    /** 最大重试次数 */
    private static final int MAX_RETRY = 3;

    /** 指数退避基数（毫秒） */
    private static final long RETRY_BASE_MS = 1000L;

    /**
     * 节点执行结果 — 同时携带正文和思考内容
     */
    private record NodeExecuteResult(String textContent, String thinkingContent) {
        boolean hasThinking() {
            return thinkingContent != null && !thinkingContent.isBlank();
        }
    }

    @Override
    public EngineType getType() {
        return EngineType.GRAPH;
    }

    @Override
    public AgentMessage execute(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        GraphAgentDefinition graphDef = (GraphAgentDefinition) def;
        log.info("GraphEngineAdapter执行: agentId={}, 节点数={}, 边数={}",
                graphDef.getAgentId(), graphDef.getGraphNodes().size(), graphDef.getGraphEdges().size());

        validateGraphConfig(graphDef);

        try {
            // 入口处注入记忆上下文（1次）
            String enrichedInput = input.getContent() != null ? input.getContent() : "";
            String memoryContext = ctx.assembleMemoryContext(enrichedInput);
            if (!memoryContext.isBlank()) {
                enrichedInput = "记忆上下文:\n" + memoryContext + "\n\n当前输入: " + enrichedInput;
            }
            ctx.appendHistory(input.getSenderId(), input.getContent(), input.getMetadata());

            boolean enableThinking = Boolean.TRUE.equals(input.getMetadataValue("enableThinking"));

            // 构建图并执行
            StateGraph graph = buildGraph(graphDef, ctx, enableThinking);
            CompiledGraph compiled = graph.compile();

            RunnableConfig config = buildRunnableConfig(ctx.getSessionId());
            Map<String, Object> graphInput = new HashMap<>();
            graphInput.put("output", enrichedInput);
            Optional<OverAllState> result = compiled.invoke(graphInput, config);

            String output = result.map(s -> (String) s.value("output").orElse("")).orElse(enrichedInput);
            // 从 StateGraph state 中提取最终节点的思考内容
            String thinkingContent = result.map(s -> {
                Object tc = s.value("thinkingContent").orElse(null);
                return tc != null ? tc.toString() : null;
            }).orElse(null);
            AgentMessage response = toAgentMessage(output, thinkingContent, graphDef.getAgentId(), ctx.getSessionId());

            // 最终响应追加历史（1次）
            ctx.appendHistory(response.getSenderId(), response.getContent(), response.getMetadata());
            return response;

        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("GraphEngineAdapter执行失败: agentId={}, error={}",
                    graphDef.getAgentId(), e.getMessage(), e);
            throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                    "Graph编排执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        GraphAgentDefinition graphDef = (GraphAgentDefinition) def;
        return Flux.defer(() -> {
            log.info("GraphEngineAdapter流式执行: agentId={}", graphDef.getAgentId());

            validateGraphConfig(graphDef);

            try {
                // 入口处注入记忆上下文（1次）
                String enrichedInput = input.getContent() != null ? input.getContent() : "";
                String memoryContext = ctx.assembleMemoryContext(enrichedInput);
                if (!memoryContext.isBlank()) {
                    enrichedInput = "记忆上下文:\n" + memoryContext + "\n\n当前输入: " + enrichedInput;
                }
                ctx.appendHistory(input.getSenderId(), input.getContent(), input.getMetadata());

                boolean enableThinking = Boolean.TRUE.equals(input.getMetadataValue("enableThinking"));

                StateGraph graph = buildGraph(graphDef, ctx, enableThinking);
                CompiledGraph compiled = graph.compile();
                RunnableConfig config = buildRunnableConfig(ctx.getSessionId());
                Map<String, Object> graphInput = new HashMap<>();
                graphInput.put("output", enrichedInput);

                String[] finalAnswer = {enrichedInput};
                String[] finalThinking = {null};

                return compiled.stream(graphInput, config)
                        .flatMap(nodeOutput -> {
                            String nodeName = nodeOutput.node();
                            String output = (String) nodeOutput.state().value("output").orElse("");
                            if (!output.isBlank()) {
                                finalAnswer[0] = output;
                            }
                            // 追踪思考内容（REPLACE 策略，最终保留叶子节点的思考）
                            nodeOutput.state().value("thinkingContent")
                                .ifPresent(tc -> finalThinking[0] = tc.toString());
                            return Flux.just(
                                    StreamEvent.nodeStart(nodeName, ctx.getSessionId()),
                                    StreamEvent.nodeEnd(nodeName, ctx.getSessionId())
                            );
                        })
                        .concatWith(Flux.defer(() -> {
                            AgentMessage response = toAgentMessage(finalAnswer[0], finalThinking[0],
                                graphDef.getAgentId(), ctx.getSessionId());
                            // 最终响应追加历史（1次）
                            ctx.appendHistory(response.getSenderId(), response.getContent(), response.getMetadata());

                            Flux<StreamEvent> thinkingFlux = Flux.empty();
                            if (finalThinking[0] != null && !finalThinking[0].isBlank()) {
                                thinkingFlux = Flux.just(StreamEvent.thinking(finalThinking[0], ctx.getSessionId()));
                            }

                            return thinkingFlux
                                    .concatWith(Flux.just(
                                            StreamEvent.textDelta(finalAnswer[0], ctx.getSessionId()),
                                            StreamEvent.done(false, null, ctx.getSessionId())
                                    ));
                        }));

            } catch (Exception e) {
                log.error("GraphEngineAdapter流式执行失败: {}", e.getMessage(), e);
                return Flux.error(new AgentException(ErrorCodeEnum.AGENT_FAILED,
                        "Graph编排流式执行失败: " + e.getMessage(), e));
            }
        }).subscribeOn(Schedulers.boundedElastic()); // 避免阻塞WebFlux事件循环
    }

    // ═══════════════════════════════════════════════════════
    // StateGraph 构建
    // ═══════════════════════════════════════════════════════

    private StateGraph buildGraph(GraphAgentDefinition def, ContextStore ctx, boolean enableThinking) throws GraphStateException {
        StateGraph graph = new StateGraph(defaultKeyStrategyFactory(
                Map.of("decision", KeyStrategy.REPLACE)
        ));

        // 1. 添加节点
        for (WorkflowNode node : def.getGraphNodes()) {
            graph.addNode(node.getId(), node_async(buildNodeAction(def, node, ctx, enableThinking)));
        }

        // 2. 添加起始边
        graph.addEdge(START, def.getGraphStart());

        // 3. 构建边映射
        Map<String, List<GraphEdge>> edgeMap = def.getGraphEdges().stream()
                .collect(Collectors.groupingBy(GraphEdge::getFrom));

        // 4. 收集有出边的节点
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
                                    log.info("Graph条件路由匹配: {} -> {} (condition={})",
                                            fromNode, edge.getTo(), edge.getCondition());
                                    return edge.getCondition();
                                }
                            }
                            log.info("Graph条件路由无匹配边，终止当前分支: nodeId={}", fromNode);
                            return "__default__";
                        }),
                        mapping);
            }

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
     * 构建节点执行动作 — 包含重试逻辑
     * 设为 protected 供 HybridEngineAdapter 复用。
     * 中间节点不注入记忆上下文，不追加历史，仅通过 StateGraph state 传递结果。
     */
    protected NodeAction buildNodeAction(AgentDefinition def, WorkflowNode node, ContextStore ctx, boolean enableThinking) {
        return state -> {
            String input = (String) state.value("output").orElse("");
            String nodeId = node.getId();

            log.info("Graph节点执行: nodeId={}, agentId={}", nodeId, node.getAgentId());

            NodeExecuteResult execResult = executeWithRetry(def, node, input, ctx, nodeId, enableThinking);

            Map<String, Object> result = new HashMap<>();
            result.put("output", execResult.textContent());
            result.put("decision", execResult.textContent());
            // 将思考内容写入 state（REPLACE 策略，最终保留叶子节点的思考内容）
            if (execResult.hasThinking()) {
                result.put("thinkingContent", execResult.thinkingContent());
            }
            return result;
        };
    }

    /**
     * 构建节点执行动作 — 不启用思考过程（兼容 HybridEngineAdapter 调用）
     */
    protected NodeAction buildNodeAction(AgentDefinition def, WorkflowNode node, ContextStore ctx) {
        return buildNodeAction(def, node, ctx, false);
    }

    // ═══════════════════════════════════════════════════════
    // 异常重试机制
    // ═══════════════════════════════════════════════════════

    private NodeExecuteResult executeWithRetry(AgentDefinition def, WorkflowNode node,
                                     String input, ContextStore ctx, String nodeId, boolean enableThinking) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                return executeNodeLogic(def, node, input, enableThinking);
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

        String errorMsg = "节点[" + nodeId + "]执行失败(重试" + MAX_RETRY + "次): "
                + lastException.getMessage();
        log.error("Graph节点重试超限: nodeId={}, 返回ERROR标记", nodeId);
        return new NodeExecuteResult(errorMsg, null);
    }

    private NodeExecuteResult executeNodeLogic(AgentDefinition def, WorkflowNode node, String input, boolean enableThinking) {
        String subAgentId = node.getAgentId();
        AgentDefinition subAgentDef = agentRegistry.get(subAgentId);

        String instruction = subAgentDef != null ? subAgentDef.getInstruction() : def.getInstruction();

        Prompt prompt;
        if (enableThinking) {
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .enableThinking(true)
                    .build();
            prompt = new Prompt(List.of(
                    new SystemMessage(instruction),
                    new UserMessage(input)
            ), options);
        } else {
            prompt = new Prompt(List.of(
                    new SystemMessage(instruction),
                    new UserMessage(input)
            ));
        }

        org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(prompt);
        ThinkingExtractor.ThinkingResult result = ThinkingExtractor.extractFromSpringAi(aiResponse);
        return new NodeExecuteResult(result.textContent(), result.hasThinking() ? result.thinkingContent() : null);
    }

    // ═══════════════════════════════════════════════════════
    // StateGraph 公共方法
    // ═══════════════════════════════════════════════════════

    protected KeyStrategyFactory defaultKeyStrategyFactory(Map<String, KeyStrategy> extraStrategies) {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("output", KeyStrategy.REPLACE);
            strategies.put("thinkingContent", KeyStrategy.REPLACE);
            strategies.put("messages", new AppendStrategy());
            strategies.putAll(extraStrategies);
            return strategies;
        };
    }

    protected RunnableConfig buildRunnableConfig(String sessionId) {
        return RunnableConfig.builder()
                .threadId(sessionId != null ? sessionId : UUID.randomUUID().toString())
                .build();
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
                        "engine", EngineType.GRAPH.name(),
                        "sessionId", sessionId != null ? sessionId : ""
                ))
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // 校验
    // ═══════════════════════════════════════════════════════

    private void validateGraphConfig(GraphAgentDefinition graphDef) {
        if (graphDef.getGraphNodes() == null || graphDef.getGraphNodes().isEmpty()) {
            throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                    "Graph配置不完整: 缺少节点定义, agentId=" + graphDef.getAgentId());
        }
        if (graphDef.getGraphStart() == null || graphDef.getGraphStart().isBlank()) {
            throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                    "Graph配置不完整: 缺少起始节点, agentId=" + graphDef.getAgentId());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 内部访问方法（供 GraphChannel 使用）
    // ═══════════════════════════════════════════════════════

    /**
     * 构建并编译 StateGraph（供 GraphChannel 获取原生对象）
     */
    public CompiledGraph buildAndCompile(GraphAgentDefinition graphDef, ContextStore ctx) {
        try {
            StateGraph graph = buildGraph(graphDef, ctx, false);
            return graph.compile();
        } catch (GraphStateException e) {
            throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                    "StateGraph编译失败: " + e.getMessage(), e);
        }
    }

}
