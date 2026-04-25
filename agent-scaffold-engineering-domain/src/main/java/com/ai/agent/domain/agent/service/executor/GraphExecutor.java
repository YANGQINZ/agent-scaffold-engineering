package com.ai.agent.domain.agent.service.executor;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.exception.AgentException;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
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
 * Graph编排执行器 — 基于Spring AI Alibaba StateGraph实现图结构编排，
 * 支持条件边路由（EdgeAction），条件路由无匹配边时终止当前分支
 */
@Slf4j
@Service
public class GraphExecutor extends AbstractGraphExecutor {

    public GraphExecutor(ChatModel chatModel, ReactExecutor reactExecutor, AgentRegistry agentRegistry) {
        super(chatModel, reactExecutor, agentRegistry);
    }

    /**
     * 同步执行Graph编排
     */
    public ChatResponse execute(AgentDefinition agentDef, ChatRequest request) {
        log.info("GraphExecutor执行: agentId={}, 节点数={}, 边数={}",
                agentDef.getAgentId(), agentDef.getGraphNodes().size(), agentDef.getGraphEdges().size());

        List<WorkflowNode> nodes = agentDef.getGraphNodes();
        List<GraphEdge> edges = agentDef.getGraphEdges();
        String startNodeId = agentDef.getGraphStart();

        if (nodes == null || nodes.isEmpty() || startNodeId == null) {
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Graph配置不完整: agentId=" + agentDef.getAgentId());
        }

        try {
            StateGraph graph = buildGraph(agentDef, nodes, edges, startNodeId, request);
            CompiledGraph compiled = compileGraph(graph);
            return invokeGraph(compiled, request, agentDef.getAgentId());
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("GraphExecutor执行失败: agentId={}, error={}", agentDef.getAgentId(), e.getMessage(), e);
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Graph编排执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式执行Graph编排
     */
    public Flux<StreamEvent> executeStream(AgentDefinition agentDef, ChatRequest request) {
        return Flux.defer(() -> {
            log.info("GraphExecutor流式执行: agentId={}", agentDef.getAgentId());

            List<WorkflowNode> nodes = agentDef.getGraphNodes();
            List<GraphEdge> edges = agentDef.getGraphEdges();
            String startNodeId = agentDef.getGraphStart();

            if (nodes == null || nodes.isEmpty() || startNodeId == null) {
                return Flux.error(new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                        "Graph配置不完整"));
            }

            try {
                StateGraph graph = buildGraph(agentDef, nodes, edges, startNodeId, request);
                CompiledGraph compiled = compileGraph(graph);
                return streamGraph(compiled, request);
            } catch (Exception e) {
                log.error("GraphExecutor流式执行失败: {}", e.getMessage(), e);
                return Flux.error(new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                        "Graph编排流式执行失败: " + e.getMessage(), e));
            }
        });
    }

    /**
     * 构建StateGraph — 添加节点、边和条件边
     */
    private StateGraph buildGraph(AgentDefinition agentDef, List<WorkflowNode> nodes,
                                  List<GraphEdge> edges, String startNodeId, ChatRequest request) throws GraphStateException {
        // decision字段用于条件边路由
        StateGraph graph = new StateGraph(defaultKeyStrategyFactory(
                Map.of("decision", KeyStrategy.REPLACE)
        ));

        // 1. 为每个Graph节点添加NodeAction
        for (WorkflowNode node : nodes) {
            String nodeId = node.getId();
            String subAgentId = node.getAgentId();
            AgentDefinition subAgentDef = findSubAgentDefinition(subAgentId);

            graph.addNode(nodeId, node_async(state -> {
                String input = state.value("output", request.getQuery());
                log.info("Graph节点执行: nodeId={}, agentId={}", nodeId, subAgentId);

                try {
                    String output;
                    if (subAgentDef != null) {
                        ChatResponse nodeResponse = executeSubAgent(subAgentDef, input, request);
                        output = nodeResponse.getAnswer();
                    } else {
                        output = executeWithChatModel(agentDef, input);
                    }

                    // 将节点输出写入decision字段，供条件边路由使用
                    Map<String, Object> result = new HashMap<>();
                    result.put("output", output);
                    result.put("decision", output);
                    result.put("messages", "节点[" + nodeId + "]执行成功");
                    return result;
                } catch (Exception e) {
                    log.warn("Graph节点失败: nodeId={}, error={}", nodeId, e.getMessage());
                    String errorOutput = "节点[" + nodeId + "]执行失败: " + e.getMessage();
                    return Map.of("output", errorOutput, "decision", errorOutput,
                            "messages", "节点[" + nodeId + "]执行失败");
                }
            }));
        }

        // 2. 添加起始边：START → start节点
        graph.addEdge(START, startNodeId);

        // 3. 构建边映射：from -> List<GraphEdge>
        Map<String, List<GraphEdge>> edgeMap = edges.stream()
                .collect(Collectors.groupingBy(GraphEdge::getFrom));

        // 4. 收集所有有出边的节点（用于后续判断叶子节点）
        Set<String> nodesWithOutEdges = edges.stream()
                .map(GraphEdge::getFrom)
                .collect(Collectors.toSet());

        // 5. 添加边和条件边
        for (Map.Entry<String, List<GraphEdge>> entry : edgeMap.entrySet()) {
            String fromNode = entry.getKey();
            List<GraphEdge> outEdges = entry.getValue();

            // 分离条件边和无条件边
            List<GraphEdge> conditionalEdges = outEdges.stream()
                    .filter(e -> e.getCondition() != null && !e.getCondition().isBlank())
                    .toList();
            List<GraphEdge> unconditionalEdges = outEdges.stream()
                    .filter(e -> e.getCondition() == null || e.getCondition().isBlank())
                    .toList();

            if (!conditionalEdges.isEmpty()) {
                // 条件边：将GraphEdge.condition映射为EdgeAction
                Map<String, String> mapping = new HashMap<>();
                for (GraphEdge edge : conditionalEdges) {
                    mapping.put(edge.getCondition(), edge.getTo());
                }
                // 条件路由无匹配边时终止当前分支（路由到END）
                mapping.put("__no_match__", END);

                graph.addConditionalEdges(fromNode,
                        edge_async(state -> {
                            String decision = state.value("decision", "");
                            for (GraphEdge edge : conditionalEdges) {
                                if (evaluateCondition(decision, edge.getCondition(), agentDef)) {
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

            // 无条件边：直接添加
            for (GraphEdge edge : unconditionalEdges) {
                graph.addEdge(fromNode, edge.getTo());
            }
        }

        // 6. 没有出边的节点自动连到END（确保图完整）
        for (WorkflowNode node : nodes) {
            if (!nodesWithOutEdges.contains(node.getId())) {
                graph.addEdge(node.getId(), END);
            }
        }

        return graph;
    }

    /**
     * 使用LLM评估条件路由 — 将节点输出与条件表达式进行匹配
     */
    private boolean evaluateCondition(String nodeOutput, String condition, AgentDefinition agentDef) {
        try {
            String evalPrompt = "你是一个条件评估器。请根据以下节点输出判断是否满足指定条件。\n\n" +
                    "条件: " + condition + "\n\n" +
                    "节点输出: " + nodeOutput + "\n\n" +
                    "请只回答'是'或'否'。如果节点输出中包含符合条件的信息，回答'是'；否则回答'否'。";

            Prompt prompt = new Prompt(List.of(
                    new SystemMessage("你是一个精确的条件判断器，只回答'是'或'否'。"),
                    new UserMessage(evalPrompt)
            ));
            String result = chatModel.call(prompt).getResult().getOutput().getText().trim();
            return result.startsWith("是") || result.toLowerCase().startsWith("yes");
        } catch (Exception e) {
            log.warn("条件评估异常: condition={}, error={}", condition, e.getMessage());
            return false;
        }
    }
}
