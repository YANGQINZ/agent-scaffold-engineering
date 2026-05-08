package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.HybridAgentDefinition;
import com.ai.agent.domain.agent.model.entity.McpServerConfig;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * Hybrid混合引擎适配器 — 外层Graph编排 + 子节点引擎委托
 * 设计思路：
 * - 外层使用 StateGraph 编排流程骨架
 * - 子节点根据 subEngines 映射委托给 Graph 引擎（复用基类 buildNodeAction）或 AgentScope 引擎
 * - 记忆上下文在入口处注入1次，最终响应追加历史1次
 * - 流式执行：中间节点同步执行发进度保活，叶子节点 token 级流式
 */
@Slf4j
@Service
public class HybridEngineAdapter extends AbstractGraphBasedAdapter {

    private final AgentScopeAdapter agentscopeAdapter;

    public HybridEngineAdapter(ChatModel chatModel,
                                AgentRegistry agentRegistry,
                                ConditionEvaluator conditionEvaluator,
                                NodeRagService nodeRagService,
                                McpToolProvider mcpToolProvider,
                                AgentScopeAdapter agentscopeAdapter) {
        super(EngineType.HYBRID, chatModel, agentRegistry, conditionEvaluator, nodeRagService, mcpToolProvider);
        this.agentscopeAdapter = agentscopeAdapter;
    }

    @Override
    public AgentMessage execute(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        HybridAgentDefinition hyDef = (HybridAgentDefinition) def;
        log.info("HybridEngineAdapter执行: agentId={}", hyDef.getAgentId());

        validateGraphConfig(hyDef.getGraphNodes(), hyDef.getGraphStart(), hyDef.getAgentId());

        try {
            String enrichedInput = enrichInputWithMemory(input, ctx);
            boolean enableThinking = Boolean.TRUE.equals(input.getMetadataValue("enableThinking"));

            StateGraph graph = buildHybridGraph(hyDef, ctx, enableThinking);
            CompiledGraph compiled = graph.compile();

            RunnableConfig config = buildRunnableConfig(ctx.getSessionId());
            Map<String, Object> graphInput = new HashMap<>();
            graphInput.put("output", enrichedInput);
            Optional<OverAllState> result = compiled.invoke(graphInput, config);

            String output = result.map(s -> (String) s.value("output").orElse("")).orElse(enrichedInput);
            String thinkingContent = result.map(s -> {
                Object tc = s.value("thinkingContent").orElse(null);
                return tc != null ? tc.toString() : null;
            }).orElse(null);

            AgentMessage response = toAgentMessage(output, thinkingContent, hyDef.getAgentId(), ctx.getSessionId());
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
        String agentId = hyDef.getAgentId();

        return wrapFluxLifecycle(() -> {
            log.info("HybridEngineAdapter流式执行: agentId={}", agentId);
            validateGraphConfig(hyDef.getGraphNodes(), hyDef.getGraphStart(), agentId);

            try {
                String enrichedInput = enrichInputWithMemory(input, ctx);
                boolean enableThinking = Boolean.TRUE.equals(input.getMetadataValue("enableThinking"));

                // 构建 Hybrid 节点 Action 映射
                Map<String, NodeAction> actions = buildHybridActions(hyDef, ctx, enableThinking);
                Set<String> leafIds = identifyLeafNodes(hyDef.getGraphNodes(), hyDef.getGraphEdges());

                if (leafIds.size() == 1) {
                    String leafId = leafIds.iterator().next();
                    WorkflowNode leafNode = hyDef.getGraphNodes().stream()
                            .filter(n -> n.getId().equals(leafId)).findFirst().orElseThrow();
                    EngineType leafEngine = resolveSubEngine(leafNode);

                    if (leafEngine == EngineType.GRAPH) {
                        return executeWithLeafStreaming(hyDef.getGraphNodes(), hyDef.getGraphStart(),
                                hyDef.getGraphEdges(), enrichedInput, ctx, enableThinking, leafNode, agentId, actions);
                    } else {
                        return executeWithAgentscopeLeaf(hyDef, enrichedInput, ctx, enableThinking, leafNode, actions);
                    }
                } else {
                    return executeWithNodeProgress(hyDef.getGraphNodes(), hyDef.getGraphStart(),
                            hyDef.getGraphEdges(), enrichedInput, ctx, enableThinking, agentId, actions);
                }
            } catch (Exception e) {
                throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                        "Hybrid编排流式执行失败: " + e.getMessage(), e);
            }
        }, agentId, ctx.getSessionId());
    }

    // ═══════════════════════════════════════════════════════════
    // Hybrid 特有的流式执行策略
    // ═══════════════════════════════════════════════════════════

    /**
     * 单叶子 AGENTSCOPE 节点：中间节点同步执行 + 叶子节点通过 executeStream() 实现 token 级流式
     * SequentialAgent 支持 pipeline.stream()，叶子节点可 token 级流式输出
     */
    private Flux<StreamEvent> executeWithAgentscopeLeaf(
            HybridAgentDefinition hyDef, String enrichedInput, ContextStore ctx,
            boolean enableThinking, WorkflowNode leafNode,
            Map<String, NodeAction> baseActions) throws GraphStateException {

        String sessionId = ctx.getSessionId();
        String leafNodeId = leafNode.getId();

        String[] intermediateOutput = {enrichedInput};
        String[] thinkingContent = {null};
        String[] textAccumulator = {""};

        // Phase 1: 构建图 — 叶子节点替换为透传
        Map<String, NodeAction> graphActions = new HashMap<>(baseActions);
        graphActions.put(leafNodeId, state -> {
            String passthrough = (String) state.value("output").orElse("");
            Map<String, Object> result = new HashMap<>();
            result.put("output", passthrough);
            result.put("decision", passthrough);
            return result;
        });

        StateGraph graph = buildGraphBase(hyDef.getGraphNodes(), hyDef.getGraphStart(),
                hyDef.getGraphEdges(), enableThinking, graphActions);
        CompiledGraph compiled = graph.compile();
        RunnableConfig config = buildRunnableConfig(sessionId);
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

        // Phase 2: 叶子节点 token 级流式（委托 AgentScopeAdapter.executeStream）
        Flux<StreamEvent> leafFlux = Flux.defer(() -> {
            String leafInput = nodeRagService.enhancePrompt(intermediateOutput[0], leafNode);
            AgentMessage subInput = AgentMessage.builder()
                    .senderId("hybrid_" + leafNodeId)
                    .content(leafInput)
                    .timestamp(System.currentTimeMillis())
                    .metadata(Map.of("role", "user", "enableThinking", enableThinking))
                    .build();

            AgentDefinition subDef = findSubAgentDef(hyDef, leafNode);

            // 直接透传流式事件，通过 doOnNext 副作用累积文本/思考内容
            return Flux.just(StreamEvent.nodeStart(leafNodeId, sessionId))
                    .concatWith(
                            agentscopeAdapter.executeStream(subDef, subInput, ctx)
                                    .doOnNext(event -> {
                                        if ("TEXT_DELTA".equals(event.getType().name()) && event.getData() != null) {
                                            Object text = event.getData().get("text");
                                            if (text instanceof String t) {
                                                textAccumulator[0] += t;
                                            }
                                        }
                                        if ("THINKING".equals(event.getType().name()) && event.getData() != null) {
                                            Object thought = event.getData().get("thought");
                                            if (thought instanceof String t && !t.isEmpty()) {
                                                thinkingContent[0] = (thinkingContent[0] == null)
                                                        ? t : thinkingContent[0] + t;
                                            }
                                        }
                                    })
                    )
                    .concatWith(Flux.just(StreamEvent.nodeEnd(leafNodeId, sessionId)));
        });

        // Phase 3: 完成阶段（所有事件流结束后发射）
        Flux<StreamEvent> doneFlux = Flux.defer(() -> {
            String finalContent = textAccumulator[0].isEmpty()
                    ? intermediateOutput[0] : textAccumulator[0];
            return buildDoneFlux(finalContent, thinkingContent[0],
                    hyDef.getAgentId(), sessionId, ctx);
        });

        return Flux.concat(intermediateFlux, leafFlux, doneFlux);
    }

    // ═══════════════════════════════════════════════════════════
    // Hybrid StateGraph 构建（包含 subEngine 分发）
    // ═══════════════════════════════════════════════════════════

    /**
     * 构建 Hybrid 所有节点的 Action 映射
     */
    private Map<String, NodeAction> buildHybridActions(HybridAgentDefinition hyDef, ContextStore ctx,
                                                        boolean enableThinking) {
        Map<String, NodeAction> actions = new HashMap<>();
        for (WorkflowNode node : hyDef.getGraphNodes()) {
            EngineType subEngine = resolveSubEngine(node);
            if (subEngine == EngineType.AGENTSCOPE) {
                actions.put(node.getId(), wrapAsGraphAction(hyDef, node, ctx, enableThinking));
            } else {
                actions.put(node.getId(), buildNodeAction(node, enableThinking));
            }
        }
        return actions;
    }

    /**
     * 构建 Hybrid StateGraph — 使用 buildHybridActions 获取完整 action 映射
     */
    private StateGraph buildHybridGraph(HybridAgentDefinition hyDef, ContextStore ctx,
                                         boolean enableThinking) throws GraphStateException {
        Map<String, NodeAction> actions = buildHybridActions(hyDef, ctx, enableThinking);
        return buildGraphBase(hyDef.getGraphNodes(), hyDef.getGraphStart(),
                hyDef.getGraphEdges(), enableThinking, actions);
    }

    /**
     * 将 AgentScope 执行包装为 StateGraph 的 NodeAction
     */
    private NodeAction wrapAsGraphAction(HybridAgentDefinition hyDef, WorkflowNode node,
                                          ContextStore ctx, boolean enableThinking) {
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

    // ═══════════════════════════════════════════════════════════
    // Hybrid 特有辅助方法
    // ═══════════════════════════════════════════════════════════

    /**
     * 解析节点的子引擎类型
     */
    private EngineType resolveSubEngine(WorkflowNode node) {
        String subEngine = node.getSubEngine();
        if (subEngine != null && !subEngine.isBlank()) {
            return EngineType.valueOf(subEngine);
        }
        return EngineType.GRAPH;
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
}
