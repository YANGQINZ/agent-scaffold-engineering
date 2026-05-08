package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.GraphAgentDefinition;
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
 * Graph引擎适配器 — 封装Spring AI Alibaba StateGraph
 * 职责：根据预定义的流程图(StateGraph)按顺序执行节点任务，
 * 支持条件跳转(策略链评估)、异常重试(指数退避≤3次)。
 * 记忆上下文在入口处注入1次，最终响应追加历史1次，中间节点不写历史。
 */
@Slf4j
@Service
public class GraphEngineAdapter extends AbstractGraphBasedAdapter {

    public GraphEngineAdapter(ChatModel chatModel,
                               AgentRegistry agentRegistry,
                               ConditionEvaluator conditionEvaluator,
                               NodeRagService nodeRagService,
                               McpToolProvider mcpToolProvider) {
        super(EngineType.GRAPH, chatModel, agentRegistry, conditionEvaluator, nodeRagService, mcpToolProvider);
    }

    @Override
    public AgentMessage execute(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        GraphAgentDefinition graphDef = (GraphAgentDefinition) def;
        log.info("GraphEngineAdapter执行: agentId={}, 节点数={}, 边数={}",
                graphDef.getAgentId(), graphDef.getGraphNodes().size(), graphDef.getGraphEdges().size());

        validateGraphConfig(graphDef.getGraphNodes(), graphDef.getGraphStart(), graphDef.getAgentId());

        try {
            String enrichedInput = enrichInputWithMemory(input, ctx);
            boolean enableThinking = Boolean.TRUE.equals(input.getMetadataValue("enableThinking"));

            Map<String, NodeAction> actions = buildNodeActionMap(graphDef.getGraphNodes(), enableThinking);
            StateGraph graph = buildGraphBase(graphDef.getGraphNodes(), graphDef.getGraphStart(),
                    graphDef.getGraphEdges(), enableThinking, actions);
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

            AgentMessage response = toAgentMessage(output, thinkingContent, graphDef.getAgentId(), ctx.getSessionId());
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
        String agentId = graphDef.getAgentId();

        return wrapFluxLifecycle(() -> {
            log.info("GraphEngineAdapter流式执行: agentId={}", agentId);
            validateGraphConfig(graphDef.getGraphNodes(), graphDef.getGraphStart(), agentId);

            try {
                String enrichedInput = enrichInputWithMemory(input, ctx);
                boolean enableThinking = Boolean.TRUE.equals(input.getMetadataValue("enableThinking"));

                Set<String> leafNodeIds = identifyLeafNodes(graphDef.getGraphNodes(), graphDef.getGraphEdges());
                Map<String, NodeAction> actions = buildNodeActionMap(graphDef.getGraphNodes(), enableThinking);

                if (leafNodeIds.size() == 1) {
                    String leafNodeId = leafNodeIds.iterator().next();
                    WorkflowNode leafNode = graphDef.getGraphNodes().stream()
                            .filter(n -> n.getId().equals(leafNodeId)).findFirst().orElseThrow();
                    return executeWithLeafStreaming(graphDef.getGraphNodes(), graphDef.getGraphStart(),
                            graphDef.getGraphEdges(), enrichedInput, ctx, enableThinking, leafNode, agentId, actions);
                } else {
                    return executeWithNodeProgress(graphDef.getGraphNodes(), graphDef.getGraphStart(),
                            graphDef.getGraphEdges(), enrichedInput, ctx, enableThinking, agentId, actions);
                }
            } catch (GraphStateException e) {
                throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                        "Graph编排流式执行失败: " + e.getMessage(), e);
            }
        }, agentId, ctx.getSessionId());
    }

    // ═══════════════════════════════════════════════════════════
    // Graph 特有方法
    // ═══════════════════════════════════════════════════════════

    /**
     * 为所有节点构建 buildNodeAction 映射
     */
    private Map<String, NodeAction> buildNodeActionMap(List<WorkflowNode> nodes, boolean enableThinking) {
        Map<String, NodeAction> actions = new HashMap<>();
        for (WorkflowNode node : nodes) {
            actions.put(node.getId(), buildNodeAction(node, enableThinking));
        }
        return actions;
    }

    /**
     * 构建并编译 StateGraph（供 GraphChannel 获取原生对象）
     */
    public CompiledGraph buildAndCompile(GraphAgentDefinition graphDef, ContextStore ctx) {
        try {
            Map<String, NodeAction> actions = buildNodeActionMap(graphDef.getGraphNodes(), false);
            StateGraph graph = buildGraphBase(graphDef.getGraphNodes(), graphDef.getGraphStart(),
                    graphDef.getGraphEdges(), false, actions);
            return graph.compile();
        } catch (GraphStateException e) {
            throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                    "StateGraph编译失败: " + e.getMessage(), e);
        }
    }
}
