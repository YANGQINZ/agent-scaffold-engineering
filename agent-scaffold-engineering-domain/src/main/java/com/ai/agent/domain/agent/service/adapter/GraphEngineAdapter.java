package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.GraphAgentDefinition;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.agent.service.engine.ConditionEvaluator;
import com.ai.agent.domain.agent.service.tool.McpToolProvider;
import com.ai.agent.domain.common.valobj.StreamEvent;
import com.ai.agent.domain.common.valobj.ThinkingExtractor;
import com.ai.agent.domain.knowledge.service.rag.NodeRagService;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
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
    private final NodeRagService nodeRagService;
    private final McpToolProvider mcpToolProvider;

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
            StateGraph graph = buildGraph(graphDef, enableThinking);
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

                // 识别叶子节点
                Set<String> leafNodeIds = identifyLeafNodes(graphDef);

                if (leafNodeIds.size() == 1) {
                    // 单叶子节点：中间节点同步执行 + 叶子节点 token 级流式
                    String leafNodeId = leafNodeIds.iterator().next();
                    WorkflowNode leafNode = graphDef.getGraphNodes().stream()
                            .filter(n -> n.getId().equals(leafNodeId)).findFirst().orElseThrow();
                    return executeWithLeafStreaming(graphDef, enrichedInput, ctx, enableThinking, leafNode);
                } else {
                    // 多叶子节点：全部同步执行，节点级渐进输出
                    return executeWithNodeProgress(graphDef, enrichedInput, ctx, enableThinking);
                }
            } catch (Exception e) {
                log.error("GraphEngineAdapter流式执行失败: {}", e.getMessage(), e);
                return Flux.error(new AgentException(ErrorCodeEnum.AGENT_FAILED,
                        "Graph编排流式执行失败: " + e.getMessage(), e));
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnError(e -> {
            if (e instanceof java.io.IOException || (e.getMessage() != null
                    && e.getMessage().contains("Broken pipe"))) {
                log.warn("GraphEngineAdapter流式输出: 客户端已断开连接, agentId={}", graphDef.getAgentId());
            }
        })
        .onErrorResume(java.io.IOException.class, e -> {
            log.warn("GraphEngineAdapter流式输出: 管道断裂，静默结束流, agentId={}", graphDef.getAgentId());
            return Flux.empty();
        })
        .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
            log.warn("GraphEngineAdapter流式输出: 执行超时, agentId={}", graphDef.getAgentId());
            return Flux.just(StreamEvent.done(false, Map.of("error", "timeout"), ctx.getSessionId()));
        });
    }

    // ═══════════════════════════════════════════════════════
    // 流式执行策略
    // ═══════════════════════════════════════════════════════

    /**
     * 识别叶子节点（无出边的节点）
     */
    private Set<String> identifyLeafNodes(GraphAgentDefinition def) {
        Set<String> nodesWithOutEdges = def.getGraphEdges().stream()
                .map(GraphEdge::getFrom).collect(Collectors.toSet());
        return def.getGraphNodes().stream()
                .map(WorkflowNode::getId)
                .filter(id -> !nodesWithOutEdges.contains(id))
                .collect(Collectors.toSet());
    }

    /**
     * 单叶子节点场景：中间节点同步执行（逐节点推送输出保活）+ 叶子节点 token 级流式
     * 历史追加在所有流完成后一次性写入。
     */
    private Flux<StreamEvent> executeWithLeafStreaming(
            GraphAgentDefinition graphDef, String enrichedInput, ContextStore ctx,
            boolean enableThinking, WorkflowNode leafNode) throws GraphStateException {

        String sessionId = ctx.getSessionId();
        String leafNodeId = leafNode.getId();

        // 累积器
        String[] intermediateOutput = {enrichedInput};
        String[] leafFullText = {""};
        String[] thinkingContent = {null};

        // Phase 1: 构建图 — 叶子节点替换为透传 Action（不调用 LLM）
        Map<String, NodeAction> overrides = Map.of(leafNodeId, state -> {
            String passthrough = (String) state.value("output").orElse("");
            Map<String, Object> result = new HashMap<>();
            result.put("output", passthrough);
            result.put("decision", passthrough);
            return result;
        });

        StateGraph graph = buildGraph(graphDef, enableThinking, overrides);
        CompiledGraph compiled = graph.compile();
        RunnableConfig config = buildRunnableConfig(sessionId);
        Map<String, Object> graphInput = new HashMap<>();
        graphInput.put("output", enrichedInput);

        Flux<StreamEvent> intermediateFlux = compiled.stream(graphInput, config)
                .flatMap(nodeOutput -> {
                    String nodeName = nodeOutput.node();
                    String output = (String) nodeOutput.state().value("output").orElse("");

                    if (nodeName.equals(leafNodeId)) {
                        // 叶子节点是透传，记录输入但不发 textDelta
                        intermediateOutput[0] = output;
                        return Flux.just(
                                StreamEvent.nodeStart(nodeName, sessionId),
                                StreamEvent.nodeEnd(nodeName, sessionId)
                        );
                    }

                    // 中间节点：仅推送进度事件保活（不发 textDelta，输出是内部处理结果）
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

        // Phase 2: 叶子节点 token 级流式
        Flux<StreamEvent> leafFlux = Flux.defer(() -> {
            String leafInput = nodeRagService.enhancePrompt(intermediateOutput[0], leafNode);
            return streamLeafNodeTokens(leafNode, leafInput, enableThinking,
                    sessionId, leafFullText, thinkingContent);
        });

        // Phase 3: 完成阶段 — 历史追加 + done
        Flux<StreamEvent> doneFlux = Flux.defer(() -> {
            String finalContent = leafFullText[0].isEmpty()
                    ? intermediateOutput[0] : leafFullText[0];
            AgentMessage response = toAgentMessage(finalContent, thinkingContent[0],
                    graphDef.getAgentId(), sessionId);
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
     * 多叶子节点场景：所有节点同步执行，每完成一个节点立即推送输出（节点级渐进）
     */
    private Flux<StreamEvent> executeWithNodeProgress(
            GraphAgentDefinition graphDef, String enrichedInput, ContextStore ctx,
            boolean enableThinking) throws GraphStateException {

        String sessionId = ctx.getSessionId();
        StateGraph graph = buildGraph(graphDef, enableThinking);
        CompiledGraph compiled = graph.compile();
        RunnableConfig config = buildRunnableConfig(sessionId);
        Map<String, Object> graphInput = new HashMap<>();
        graphInput.put("output", enrichedInput);

        String[] finalAnswer = {enrichedInput};
        String[] finalThinking = {null};

        // 收集所有叶子节点 ID（最后一批节点，它们的输出是最终答案）
        Set<String> leafIds = identifyLeafNodes(graphDef);

        return compiled.stream(graphInput, config)
                .flatMap(nodeOutput -> {
                    String nodeName = nodeOutput.node();
                    String output = (String) nodeOutput.state().value("output").orElse("");
                    if (!output.isBlank()) {
                        finalAnswer[0] = output;
                    }
                    nodeOutput.state().value("thinkingContent")
                            .ifPresent(tc -> finalThinking[0] = tc.toString());

                    // 叶子节点：输出就是最终答案，发 textDelta
                    // 中间节点：仅发进度事件
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
                            graphDef.getAgentId(), sessionId);
                    ctx.appendHistory(response.getSenderId(), response.getContent(), response.getMetadata());

                    Flux<StreamEvent> thinkingFlux = Flux.empty();
                    if (finalThinking[0] != null && !finalThinking[0].isBlank()) {
                        thinkingFlux = Flux.just(StreamEvent.thinking(finalThinking[0], sessionId));
                    }
                    return thinkingFlux.concatWith(Flux.just(StreamEvent.done(false, null, sessionId)));
                }));
    }

    /**
     * 叶子节点 token 级流式输出 — 复用 SimpleChatStrategy 的流式模式
     * 当模型不支持流式时，自动降级为同步调用
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
            // MCP客户端初始化失败等，降级为同步调用
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
     * 叶子节点同步降级 — 模型不支持流式或流式失败时调用
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
     */
    protected Flux<StreamEvent> extractStreamingToken(
            org.springframework.ai.chat.model.ChatResponse cr, String sessionId,
            String[] textAccumulator, String[] thinkingAccumulator) {

        if (cr.getResult() == null || cr.getResult().getOutput() == null) {
            return Flux.empty();
        }

        // 复用 ThinkingExtractor 统一提取
        ThinkingExtractor.ThinkingResult result = ThinkingExtractor.extractFromSpringAi(cr);
        Flux<StreamEvent> events = Flux.empty();

        if (result.hasThinking()) {
            thinkingAccumulator[0] = (thinkingAccumulator[0] == null)
                    ? result.thinkingContent() : thinkingAccumulator[0] + result.thinkingContent();
            events = events.concatWith(Flux.just(StreamEvent.thinking(result.thinkingContent(), sessionId)));
        }
        if (!result.textContent().isEmpty()) {
            textAccumulator[0] += result.textContent();
            events = events.concatWith(Flux.just(StreamEvent.textDelta(result.textContent(), sessionId)));
        }
        return events;
    }

    /**
     * 解析节点的 system prompt（复用 executeNodeLogic 的逻辑）
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
     * 构建节点 Prompt（复用 executeNodeLogic 的逻辑）
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

    // ═══════════════════════════════════════════════════════
    // StateGraph 构建
    // ═══════════════════════════════════════════════════════

    private StateGraph buildGraph(GraphAgentDefinition def, boolean enableThinking) throws GraphStateException {
        return buildGraph(def, enableThinking, Collections.emptyMap());
    }

    /**
     * 构建 StateGraph，支持指定节点使用自定义 Action（用于流式场景下叶子节点透传）
     */
    private StateGraph buildGraph(GraphAgentDefinition def, boolean enableThinking,
                                  Map<String, NodeAction> actionOverrides) throws GraphStateException {
        StateGraph graph = new StateGraph(defaultKeyStrategyFactory(
                Map.of("decision", KeyStrategy.REPLACE)
        ));

        // 1. 添加节点 — 有覆盖的用覆盖，否则用默认 Action
        for (WorkflowNode node : def.getGraphNodes()) {
            NodeAction action = actionOverrides.getOrDefault(node.getId(),
                    buildNodeAction(node, enableThinking));
            graph.addNode(node.getId(), node_async(action));
        }

        // 2. 添加起始边（支持多起始节点）
        for (String startTarget : def.getGraphStart()) {
            graph.addEdge(START, startTarget);
        }

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
    protected NodeAction buildNodeAction(WorkflowNode node, boolean enableThinking) {
        return state -> {
            String input = (String) state.value("output").orElse("");
            String nodeId = node.getId();

            log.info("Graph节点执行: nodeId={}, agentId={}", nodeId, node.getAgentId());

            // 节点级 RAG 增强：根据节点的 ragEnabled/knowledgeBaseId 配置决定是否增强
            input = nodeRagService.enhancePrompt(input, node);

            NodeExecuteResult execResult = executeWithRetry(node, input, nodeId, enableThinking);

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

    // ═══════════════════════════════════════════════════════
    // 异常重试机制
    // ═══════════════════════════════════════════════════════

    private NodeExecuteResult executeWithRetry(WorkflowNode node,
                                     String input, String nodeId, boolean enableThinking) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                return executeNodeLogic(node, input, enableThinking);
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

    private NodeExecuteResult executeNodeLogic(WorkflowNode node, String input, boolean enableThinking) {
        // 1. 解析 instruction：节点内联 > agentId 子代理 > 默认
        String systemPrompt;
        if (node.getInstruction() != null && !node.getInstruction().isBlank()) {
            systemPrompt = node.getInstruction();
        } else if (node.getAgentId() != null && !node.getAgentId().isBlank()) {
            AgentDefinition subAgentDef = agentRegistry.get(node.getAgentId());
            systemPrompt = (subAgentDef != null && subAgentDef.getInstruction() != null)
                    ? subAgentDef.getInstruction()
                    : "你是一个有用的助手。";
        } else {
            systemPrompt = "你是一个有用的助手。";
        }

        // 2. 构建 Prompt
        Prompt prompt;
        if (enableThinking) {
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .enableThinking(true)
                    .build();
            prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(input)
            ), options);
        } else {
            prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(input)
            ));
        }

        // 3. 执行：MCP 工具调用或普通 LLM 调用（MCP失败时降级为普通调用）
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

        // 4. 提取结果
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
        if (graphDef.getGraphStart() == null || graphDef.getGraphStart().isEmpty()) {
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
            StateGraph graph = buildGraph(graphDef, false);
            return graph.compile();
        } catch (GraphStateException e) {
            throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                    "StateGraph编译失败: " + e.getMessage(), e);
        }
    }

}
