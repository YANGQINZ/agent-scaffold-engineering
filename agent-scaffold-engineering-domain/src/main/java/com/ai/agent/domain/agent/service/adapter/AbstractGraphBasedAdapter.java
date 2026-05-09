package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.agent.service.engine.ConditionEvaluator;
import com.ai.agent.domain.agent.service.tool.McpToolProvider;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.common.valobj.StreamEvent;
import com.ai.agent.domain.common.valobj.ThinkingExtractor;
import com.ai.agent.domain.knowledge.service.rag.NodeRagService;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.exception.AgentException;
import com.ai.agent.types.exception.enums.ErrorCodeEnum;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

/**
 * Graph 系引擎适配器抽象中间类 — Graph + Hybrid 共用的 StateGraph 编排逻辑
 *
 * <p>封装 GraphEngineAdapter 和 HybridEngineAdapter 的共同方法：</p>
 * <ul>
 *   <li>StateGraph 构建骨架（节点、边、条件边、叶子→END）</li>
 *   <li>叶子节点识别、单叶子流式策略、多叶子渐进策略</li>
 *   <li>节点执行（含重试）、叶子节点 token 级流式、同步降级</li>
 *   <li>RunnableConfig / KeyStrategyFactory 构建</li>
 * </ul>
 *
 * <p>子类通过 {@link #resolveNodeAction} 定义节点 Action 创建策略（Graph 使用重试逻辑，
 * Hybrid 根据 subEngine 分发到不同引擎）。</p>
 */
@Slf4j
public abstract class AbstractGraphBasedAdapter extends AbstractEngineAdapter {

    /** 最大重试次数 */
    private static final int MAX_RETRY = 3;

    /** 指数退避基数（毫秒） */
    private static final long RETRY_BASE_MS = 1000L;

    protected final ChatModel chatModel;
    protected final AgentRegistry agentRegistry;
    protected final ConditionEvaluator conditionEvaluator;
    protected final NodeRagService nodeRagService;
    protected final McpToolProvider mcpToolProvider;

    protected AbstractGraphBasedAdapter(EngineType engineType,
                                         ChatModel chatModel,
                                         AgentRegistry agentRegistry,
                                         ConditionEvaluator conditionEvaluator,
                                         NodeRagService nodeRagService,
                                         McpToolProvider mcpToolProvider) {
        super(engineType);
        this.chatModel = chatModel;
        this.agentRegistry = agentRegistry;
        this.conditionEvaluator = conditionEvaluator;
        this.nodeRagService = nodeRagService;
        this.mcpToolProvider = mcpToolProvider;
    }

    // ═══════════════════════════════════════════════════════════
    // 叶子节点识别
    // ═══════════════════════════════════════════════════════════

    /**
     * 识别叶子节点（无出边的节点）
     *
     * @param nodes 所有节点
     * @param edges 所有边
     * @return 叶子节点 ID 集合
     */
    protected Set<String> identifyLeafNodes(List<WorkflowNode> nodes, List<GraphEdge> edges) {
        Set<String> nodesWithOutEdges = edges.stream()
                .map(GraphEdge::getFrom).collect(Collectors.toSet());
        return nodes.stream()
                .map(WorkflowNode::getId)
                .filter(id -> !nodesWithOutEdges.contains(id))
                .collect(Collectors.toSet());
    }

    // ═══════════════════════════════════════════════════════════
    // StateGraph 公共构建
    // ═══════════════════════════════════════════════════════════

    /**
     * 构建 StateGraph 骨架 — 节点添加、起始边、条件边、叶子→END
     *
     * <p>调用方需提供完整的节点 Action 映射（每个节点 ID 对应一个 NodeAction）。
     * 子类通过不同方式构建 action 映射：Graph 使用 buildNodeAction，Hybrid 根据 subEngine 分发。</p>
     *
     * @param nodes           节点列表
     * @param startNodes      起始节点 ID 列表
     * @param edges           边列表
     * @param enableThinking  是否启用深度思考
     * @param nodeActions     所有节点的 Action 映射（节点ID → NodeAction）
     * @return 构建好的 StateGraph
     */
    protected StateGraph buildGraphBase(List<WorkflowNode> nodes, List<String> startNodes,
                                         List<GraphEdge> edges, boolean enableThinking,
                                         Map<String, NodeAction> nodeActions) throws GraphStateException {
        StateGraph graph = new StateGraph(defaultKeyStrategyFactory(
                Map.of("decision", KeyStrategy.REPLACE)
        ));

        // 1. 添加节点 — 使用调用方提供的完整 action 映射
        for (WorkflowNode node : nodes) {
            NodeAction action = nodeActions.get(node.getId());
            if (action == null) {
                throw new GraphStateException("节点 " + node.getId() + " 没有 NodeAction 映射");
            }
            graph.addNode(node.getId(), node_async(action));
        }

        // 2. 添加起始边（支持多起始节点）
        for (String startTarget : startNodes) {
            graph.addEdge(START, startTarget);
        }

        // 3. 构建边映射
        Map<String, List<GraphEdge>> edgeMap = edges.stream()
                .collect(Collectors.groupingBy(GraphEdge::getFrom));

        Set<String> nodesWithOutEdges = edges.stream()
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
                                    log.info("{}条件路由匹配: {} -> {} (condition={})",
                                            getType().name(), fromNode, edge.getTo(), edge.getCondition());
                                    return edge.getCondition();
                                }
                            }
                            log.info("{}条件路由无匹配边，终止当前分支: nodeId={}", getType().name(), fromNode);
                            return "__default__";
                        }),
                        mapping);
            }

            for (GraphEdge edge : unconditionalEdges) {
                graph.addEdge(fromNode, edge.getTo());
            }
        }

        // 5. 叶子节点自动连到 END
        for (WorkflowNode node : nodes) {
            if (!nodesWithOutEdges.contains(node.getId())) {
                graph.addEdge(node.getId(), END);
            }
        }

        return graph;
    }

    // ═══════════════════════════════════════════════════════════
    // 流式执行策略
    // ═══════════════════════════════════════════════════════════

    /**
     * 单叶子节点场景：中间节点同步执行（逐节点推送进度保活）+ 叶子节点 token 级流式
     *
     * @param nodes          所有节点
     * @param startNodes     起始节点 ID 列表
     * @param edges          所有边
     * @param enrichedInput  增强后的输入
     * @param ctx            上下文存储
     * @param enableThinking 是否启用深度思考
     * @param leafNode       叶子节点
     * @param agentId        Agent ID
     * @return 流式事件 Flux
     */
    protected Flux<StreamEvent> executeWithLeafStreaming(
            List<WorkflowNode> nodes, List<String> startNodes, List<GraphEdge> edges,
            String enrichedInput, ContextStore ctx, boolean enableThinking,
            WorkflowNode leafNode, String agentId,
            Map<String, NodeAction> baseActions) throws GraphStateException {

        log.info("{}executeWithLeafStreaming: leafNodeId={}, nodes={}", getType().name(), leafNode.getId(),
                nodes.stream().map(WorkflowNode::getId).toList());

        String sessionId = ctx.getSessionId();
        String leafNodeId = leafNode.getId();

        // 累积器
        String[] intermediateOutput = {enrichedInput};
        String[] leafFullText = {""};
        String[] thinkingContent = {null};

        // Phase 1: 构建图 — 叶子节点替换为透传 Action（不调用 LLM）
        Map<String, NodeAction> graphActions = new HashMap<>(baseActions);
        graphActions.put(leafNodeId, state -> {
            String passthrough = (String) state.value("output").orElse("");
            Map<String, Object> result = new HashMap<>();
            result.put("output", passthrough);
            result.put("decision", passthrough);
            return result;
        });

        StateGraph graph = buildGraphBase(nodes, startNodes, edges, enableThinking, graphActions);
        CompiledGraph compiled = graph.compile();
        RunnableConfig config = buildRunnableConfig(sessionId);
        Map<String, Object> graphInput = new HashMap<>();
        graphInput.put("output", enrichedInput);

        Flux<StreamEvent> intermediateFlux = compiled.stream(graphInput, config)
                .flatMap(nodeOutput -> {
                    String nodeName = nodeOutput.node();
                    String output = (String) nodeOutput.state().value("output").orElse("");

                    log.info("{}LeafStreaming中间节点: nodeName={}, outputLength={}", getType().name(), nodeName, output.length());

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
                })
                .doOnComplete(() -> log.info("{}LeafStreaming Phase1完成: intermediateOutputLength={}",
                        getType().name(), intermediateOutput[0].length()));

        // Phase 2: 叶子节点 token 级流式
        Flux<StreamEvent> leafFlux = Flux.defer(() -> {
            log.info("{}LeafStreaming Phase2: 开始叶子节点流式, leafNodeId={}, inputLength={}",
                    getType().name(), leafNodeId, intermediateOutput[0].length());
            String leafInput = nodeRagService.enhancePrompt(intermediateOutput[0], leafNode);
            return streamLeafNodeTokens(leafNode, leafInput, enableThinking,
                    sessionId, leafFullText, thinkingContent);
        });

        // Phase 3: 完成阶段
        Flux<StreamEvent> doneFlux = Flux.defer(() -> {
            String finalContent = leafFullText[0].isEmpty()
                    ? intermediateOutput[0] : leafFullText[0];
            log.info("{}LeafStreaming完成: finalContentLength={}, hasThinking={}",
                    getType().name(), finalContent.length(), thinkingContent[0] != null);
            return buildDoneFlux(finalContent, thinkingContent[0], agentId, sessionId, ctx);
        });

        return Flux.concat(intermediateFlux, leafFlux, doneFlux);
    }

    /**
     * 多叶子节点场景：所有节点同步执行，每完成一个节点立即推送输出（节点级渐进）
     *
     * @param nodes          所有节点
     * @param startNodes     起始节点 ID 列表
     * @param edges          所有边
     * @param enrichedInput  增强后的输入
     * @param ctx            上下文存储
     * @param enableThinking 是否启用深度思考
     * @param agentId        Agent ID
     * @param baseActions    所有节点的 Action 映射
     * @return 流式事件 Flux
     */
    protected Flux<StreamEvent> executeWithNodeProgress(
            List<WorkflowNode> nodes, List<String> startNodes, List<GraphEdge> edges,
            String enrichedInput, ContextStore ctx, boolean enableThinking,
            String agentId, Map<String, NodeAction> baseActions) throws GraphStateException {

        Set<String> leafIds = identifyLeafNodes(nodes, edges);
        log.info("{}executeWithNodeProgress入口: nodes={}, leafIds={}, inputLength={}, hasEdges={}",
                getType().name(),
                nodes.stream().map(WorkflowNode::getId).toList(),
                leafIds, enrichedInput.length(),
                edges != null && !edges.isEmpty());

        String sessionId = ctx.getSessionId();

        // 无边（独立节点）：每个叶子节点 token 级流式，实现真正的流式输出
        if (edges == null || edges.isEmpty()) {
            return executeIndependentNodesStreaming(nodes, enrichedInput,
                    enableThinking, agentId, sessionId, ctx);
        }

        // 有边（有依赖的图）：invoke 同步执行（compiled.stream() 对并行节点不发射单个节点事件）
        return executeGraphWithEdges(nodes, startNodes, edges, enrichedInput,
                enableThinking, agentId, sessionId, ctx, baseActions, leafIds);
    }

    /**
     * 独立节点流式执行 — 每个叶子节点 token 级流式，顺序执行
     * 适用于无边（无依赖）的多节点场景，提供逐 token 的流式体验
     */
    private Flux<StreamEvent> executeIndependentNodesStreaming(
            List<WorkflowNode> nodes, String enrichedInput,
            boolean enableThinking, String agentId, String sessionId,
            ContextStore ctx) {

        String[] finalAnswer = {""};
        String[] finalThinking = {null};

        List<Flux<StreamEvent>> nodeFluxes = new ArrayList<>();
        for (WorkflowNode node : nodes) {
            String nodeId = node.getId();
            String[] textAcc = {""};
            String[] thinkAcc = {null};

            Flux<StreamEvent> nodeFlux = Flux.defer(() -> {
                log.info("{}独立节点流式开始: nodeId={}", getType().name(), nodeId);
                String input = nodeRagService.enhancePrompt(enrichedInput, node);
                return Flux.just(StreamEvent.nodeStart(nodeId, sessionId))
                        .concatWith(streamLeafNodeTokens(node, input, enableThinking,
                                sessionId, textAcc, thinkAcc))
                        .doOnComplete(() -> {
                            if (!textAcc[0].isBlank()) {
                                finalAnswer[0] = textAcc[0];
                            }
                            if (thinkAcc[0] != null) {
                                finalThinking[0] = thinkAcc[0];
                            }
                            log.info("{}独立节点流式完成: nodeId={}, outputLength={}, hasThinking={}",
                                    getType().name(), nodeId, textAcc[0].length(), thinkAcc[0] != null);
                        })
                        .concatWith(Flux.just(StreamEvent.nodeEnd(nodeId, sessionId)));
            });
            nodeFluxes.add(nodeFlux);
        }

        return Flux.concat(nodeFluxes)
                .concatWith(Flux.defer(() ->
                        buildDoneFlux(finalAnswer[0], finalThinking[0], agentId, sessionId, ctx)));
    }

    /**
     * 有依赖的图执行 — invoke 同步执行后一次性输出
     * compiled.stream() 对并行节点只发射 __START__/__END__，不发射单个节点事件
     */
    private Flux<StreamEvent> executeGraphWithEdges(
            List<WorkflowNode> nodes, List<String> startNodes, List<GraphEdge> edges,
            String enrichedInput, boolean enableThinking, String agentId,
            String sessionId, ContextStore ctx, Map<String, NodeAction> baseActions,
            Set<String> leafIds) throws GraphStateException {

        StateGraph graph = buildGraphBase(nodes, startNodes, edges, enableThinking, baseActions);
        CompiledGraph compiled = graph.compile();
        log.info("{}executeWithNodeProgress图构建完成(有边), 开始invoke执行", getType().name());
        RunnableConfig config = buildRunnableConfig(sessionId);
        Map<String, Object> graphInput = new HashMap<>();
        graphInput.put("output", enrichedInput);

        Optional<OverAllState> result;
        try {
            result = compiled.invoke(graphInput, config);
        } catch (Exception e) {
            log.error("{}executeWithNodeProgress invoke失败: {}", getType().name(), e.getMessage(), e);
            return Flux.just(StreamEvent.done(false, Map.of("error", e.getMessage()), sessionId));
        }

        String finalAnswer = result.map(s -> (String) s.value("output").orElse("")).orElse("");
        String finalThinking = result.map(s -> {
            Object tc = s.value("thinkingContent").orElse(null);
            return tc != null ? tc.toString() : null;
        }).orElse(null);

        log.info("{}executeWithNodeProgress invoke完成: finalAnswer长度={}, hasThinking={}",
                getType().name(), finalAnswer.length(), finalThinking != null);

        List<StreamEvent> events = new ArrayList<>();
        for (WorkflowNode node : nodes) {
            String nodeId = node.getId();
            events.add(StreamEvent.nodeStart(nodeId, sessionId));
            if (leafIds.contains(nodeId) && !finalAnswer.isBlank()) {
                events.add(StreamEvent.textDelta(finalAnswer, sessionId));
            }
            events.add(StreamEvent.nodeEnd(nodeId, sessionId));
        }

        return Flux.fromIterable(events)
                .concatWith(Flux.defer(() ->
                        buildDoneFlux(finalAnswer, finalThinking, agentId, sessionId, ctx)));
    }

    // ═══════════════════════════════════════════════════════════
    // 节点执行（含重试）
    // ═══════════════════════════════════════════════════════════

    /**
     * 节点执行结果 — 同时携带正文和思考内容
     */
    protected record NodeExecuteResult(String textContent, String thinkingContent) {
        boolean hasThinking() {
            return thinkingContent != null && !thinkingContent.isBlank();
        }
    }

    /**
     * 构建标准节点执行动作 — 含 RAG 增强 + 重试逻辑
     *
     * @param node           工作流节点
     * @param enableThinking 是否启用深度思考
     * @return NodeAction
     */
    protected NodeAction buildNodeAction(WorkflowNode node, boolean enableThinking) {
        return state -> {
            String input = (String) state.value("output").orElse("");
            String nodeId = node.getId();

            log.info("{}节点执行开始: nodeId={}, agentId={}, inputLength={}",
                    getType().name(), nodeId, node.getAgentId(), input.length());

            // 节点级 RAG 增强
            input = nodeRagService.enhancePrompt(input, node);

            NodeExecuteResult execResult = executeWithRetry(node, input, nodeId, enableThinking);

            log.info("{}节点执行完成: nodeId={}, outputLength={}, hasThinking={}",
                    getType().name(), nodeId, execResult.textContent().length(), execResult.hasThinking());

            Map<String, Object> result = new HashMap<>();
            result.put("output", execResult.textContent());
            result.put("decision", execResult.textContent());
            if (execResult.hasThinking()) {
                result.put("thinkingContent", execResult.thinkingContent());
            }
            return result;
        };
    }

    /**
     * 带重试的节点执行 — 指数退避，最多重试 MAX_RETRY 次
     */
    private NodeExecuteResult executeWithRetry(WorkflowNode node,
                                                String input, String nodeId, boolean enableThinking) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                return executeNodeLogic(node, input, enableThinking);
            } catch (Exception e) {
                lastException = e;
                log.warn("{}节点执行失败(第{}/{}次): nodeId={}, error={}",
                        getType().name(), attempt, MAX_RETRY, nodeId, e.getMessage());

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
        log.error("{}节点重试超限: nodeId={}, 返回ERROR标记", getType().name(), nodeId);
        return new NodeExecuteResult(errorMsg, null);
    }

    /**
     * 单次节点执行 — 解析指令、构建 Prompt、调用 LLM、提取结果
     */
    private NodeExecuteResult executeNodeLogic(WorkflowNode node, String input, boolean enableThinking) {
        String systemPrompt = resolveSystemPrompt(node);
        Prompt prompt = buildNodePrompt(systemPrompt, input, enableThinking);

        // MCP 工具调用或普通 LLM 调用
        org.springframework.ai.chat.model.ChatResponse aiResponse;
        if (node.getMcpServers() != null && !node.getMcpServers().isEmpty()) {
            try {
                List<ToolCallback> tools = mcpToolProvider.buildGraphTools(node.getMcpServers());
                aiResponse = ChatClient.create(chatModel)
                        .prompt(prompt)
                        .toolCallbacks(tools)
                        .call()
                        .chatResponse();
            } catch (Exception e) {
                log.warn("MCP工具初始化失败，降级为普通LLM调用: nodeId={}, error={}",
                        node.getId(), e.getMessage());
                aiResponse = chatModel.call(prompt);
            }
        } else {
            aiResponse = chatModel.call(prompt);
        }

        ThinkingExtractor.ThinkingResult result = ThinkingExtractor.extractFromSpringAi(aiResponse);
        return new NodeExecuteResult(result.textContent(), result.hasThinking() ? result.thinkingContent() : null);
    }

    // ═══════════════════════════════════════════════════════════
    // 叶子节点 token 级流式
    // ═══════════════════════════════════════════════════════════

    /**
     * 叶子节点 token 级流式输出 — 当模型不支持流式时自动降级为同步调用
     */
    protected Flux<StreamEvent> streamLeafNodeTokens(
            WorkflowNode node, String input, boolean enableThinking,
            String sessionId, String[] textAccumulator, String[] thinkingAccumulator) {

        String systemPrompt = resolveSystemPrompt(node);
        Prompt prompt = buildNodePrompt(systemPrompt, input, enableThinking);

        Flux<org.springframework.ai.chat.model.ChatResponse> streamFlux;
        try {
            if (node.getMcpServers() != null && !node.getMcpServers().isEmpty()) {
                List<ToolCallback> tools = mcpToolProvider.buildGraphTools(node.getMcpServers());
                streamFlux = ChatClient.create(chatModel)
                        .prompt(prompt).toolCallbacks(tools).stream().chatResponse();
            } else {
                streamFlux = chatModel.stream(prompt);
            }
        } catch (Exception e) {
            log.warn("叶子节点流式初始化失败，降级为同步调用: {}", e.getMessage());
            return syncLeafNodeFallback(prompt, sessionId, textAccumulator, thinkingAccumulator);
        }

        return streamFlux
                .flatMap(cr -> extractStreamingToken(cr, sessionId, textAccumulator, thinkingAccumulator))
                .onErrorResume(e -> {
                    log.warn("叶子节点流式输出失败，降级为同步调用: {}", e.getMessage());
                    return syncLeafNodeFallback(prompt, sessionId, textAccumulator, thinkingAccumulator);
                });
    }

    /**
     * 叶子节点同步降级
     */
    protected Flux<StreamEvent> syncLeafNodeFallback(
            Prompt prompt, String sessionId,
            String[] textAccumulator, String[] thinkingAccumulator) {
        try {
            org.springframework.ai.chat.model.ChatResponse syncResponse = chatModel.call(prompt);
            ThinkingExtractor.ThinkingResult result = ThinkingExtractor.extractFromSpringAi(syncResponse);
            textAccumulator[0] = result.textContent();
            if (result.hasThinking()) {
                thinkingAccumulator[0] = result.thinkingContent();
            }
            Flux<StreamEvent> events = Flux.empty();
            if (result.hasThinking()) {
                events = events.concatWith(Flux.just(
                        StreamEvent.thinking(result.thinkingContent(), sessionId)));
            }
            return events.concatWith(Flux.just(
                    StreamEvent.textDelta(result.textContent(), sessionId)));
        } catch (Exception fallbackEx) {
            log.error("叶子节点同步降级也失败: {}", fallbackEx.getMessage(), fallbackEx);
            return Flux.error(fallbackEx);
        }
    }

    /**
     * 从流式 ChatResponse 中提取 token 级事件
     *
     * <p>DashScope 思考模式严格分两阶段：先 reasoningContent（思考）→ 后 chunk（文本）。
     * 当同一个 chunk 同时携带思考内容和文本时，只发射 TEXT_DELTA，避免思考和文本交替输出。</p>
     */
    protected Flux<StreamEvent> extractStreamingToken(
            org.springframework.ai.chat.model.ChatResponse cr, String sessionId,
            String[] textAccumulator, String[] thinkingAccumulator) {

        if (cr.getResult() == null || cr.getResult().getOutput() == null) {
            return Flux.empty();
        }

        ThinkingExtractor.ThinkingResult result = ThinkingExtractor.extractFromSpringAi(cr);

        // 文本输出阶段：只发射文本 delta，不再发射思考事件
        if (!result.textContent().isEmpty()) {
            textAccumulator[0] += result.textContent();
            return Flux.just(StreamEvent.textDelta(result.textContent(), sessionId));
        }

        // 思考阶段：发射增量思考内容，静默累积到 accumulator
        if (result.hasThinking()) {
            thinkingAccumulator[0] = (thinkingAccumulator[0] == null)
                    ? result.thinkingContent() : thinkingAccumulator[0] + result.thinkingContent();
            return Flux.just(StreamEvent.thinking(result.thinkingContent(), sessionId));
        }
        return Flux.empty();
    }

    // ═══════════════════════════════════════════════════════════
    // Prompt 构建
    // ═══════════════════════════════════════════════════════════

    /**
     * 解析节点的 system prompt
     */
    protected String resolveSystemPrompt(WorkflowNode node) {
        if (node.getInstruction() != null && !node.getInstruction().isBlank()) {
            return node.getInstruction();
        }
        if (node.getAgentId() != null && !node.getAgentId().isBlank()) {
            AgentDefinition subAgentDef = agentRegistry.get(node.getAgentId());
            if (subAgentDef != null && subAgentDef.getInstruction() != null) {
                return subAgentDef.getInstruction();
            }
        }
        return "你是一个有用的助手。";
    }

    /**
     * 构建节点 Prompt — enableThinking 时注入 DashScopeChatOptions
     */
    protected Prompt buildNodePrompt(String systemPrompt, String input, boolean enableThinking) {
        if (enableThinking) {
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .enableThinking(true).build();
            return new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(input)
            ), options);
        }
        return new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(input)
        ));
    }

    // ═══════════════════════════════════════════════════════════
    // StateGraph 公共配置
    // ═══════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════
    // 校验
    // ═══════════════════════════════════════════════════════════

    /**
     * 校验图配置完整性
     */
    protected void validateGraphConfig(List<WorkflowNode> nodes, List<String> startNodes, String agentId) {
        if (nodes == null || nodes.isEmpty()) {
            throw new AgentException(ErrorCodeEnum.AGENT_FAILED.getErrorCode(),
                    getType().name() + "配置不完整: 缺少节点定义, agentId=" + agentId);
        }
        if (startNodes == null || startNodes.isEmpty()) {
            throw new AgentException(ErrorCodeEnum.AGENT_FAILED.getErrorCode(),
                    getType().name() + "配置不完整: 缺少起始节点, agentId=" + agentId);
        }
    }
}
