package com.ai.agent.domain.agent.service.executor;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Workflow编排执行器 — 基于Spring AI Alibaba StateGraph实现线性Workflow编排，
 * 节点按顺序执行，前节点输出作为后节点输入，节点失败时传递错误信息继续执行
 */
@Slf4j
@Service
public class WorkflowExecutor extends AbstractGraphExecutor {

    public WorkflowExecutor(ChatModel chatModel, ReactExecutor reactExecutor, AgentRegistry agentRegistry) {
        super(chatModel, reactExecutor, agentRegistry);
    }

    /**
     * 同步执行Workflow编排
     * 构建StateGraph：START → node1 → node2 → ... → END，编译后invoke
     */
    public ChatResponse execute(AgentDefinition agentDef, ChatRequest request) {
        log.info("WorkflowExecutor执行: agentId={}, 节点数={}", agentDef.getAgentId(), agentDef.getWorkflowNodes().size());

        List<WorkflowNode> nodes = agentDef.getWorkflowNodes();
        if (nodes == null || nodes.isEmpty()) {
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Workflow节点列表为空: agentId=" + agentDef.getAgentId());
        }

        try {
            StateGraph workflow = buildWorkflowGraph(agentDef, nodes, request);
            CompiledGraph compiled = compileGraph(workflow);
            return invokeGraph(compiled, request, agentDef.getAgentId());
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("WorkflowExecutor执行失败: agentId={}, error={}", agentDef.getAgentId(), e.getMessage(), e);
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Workflow编排执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式执行Workflow编排 — 利用StateGraph.stream()获取节点执行流，
     * 映射为NODE_START/NODE_END事件，从最终节点输出提取答案
     */
    public Flux<StreamEvent> executeStream(AgentDefinition agentDef, ChatRequest request) {
        return Flux.defer(() -> {
            log.info("WorkflowExecutor流式执行: agentId={}", agentDef.getAgentId());

            List<WorkflowNode> nodes = agentDef.getWorkflowNodes();
            if (nodes == null || nodes.isEmpty()) {
                return Flux.error(new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                        "Workflow节点列表为空: agentId=" + agentDef.getAgentId()));
            }

            try {
                StateGraph workflow = buildWorkflowGraph(agentDef, nodes, request);
                CompiledGraph compiled = compileGraph(workflow);
                return streamGraph(compiled, request);
            } catch (Exception e) {
                log.error("WorkflowExecutor流式执行失败: {}", e.getMessage(), e);
                return Flux.error(new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                        "Workflow编排流式执行失败: " + e.getMessage(), e));
            }
        });
    }

    /**
     * 构建Workflow StateGraph（复用于execute和executeStream）
     */
    private StateGraph buildWorkflowGraph(AgentDefinition agentDef, List<WorkflowNode> nodes,
                                          ChatRequest request) throws GraphStateException {
        StateGraph workflow = new StateGraph(defaultKeyStrategyFactory());

        for (WorkflowNode node : nodes) {
            String nodeId = node.getId();
            String subAgentId = node.getAgentId();
            AgentDefinition subAgentDef = findSubAgentDefinition(subAgentId);

            workflow.addNode(nodeId, node_async(state -> {
                String input = state.value("output", request.getQuery());
                log.info("Workflow节点执行: nodeId={}, agentId={}", nodeId, subAgentId);

                try {
                    String output;
                    if (subAgentDef != null && subAgentDef.getMode() != null) {
                        ChatResponse nodeResponse = executeSubAgent(subAgentDef, input, request);
                        output = nodeResponse.getAnswer();
                    } else {
                        output = executeWithChatModel(agentDef, input);
                    }
                    return Map.of("output", output, "messages", "节点[" + nodeId + "]执行成功");
                } catch (Exception e) {
                    log.warn("Workflow节点失败: nodeId={}, error={}", nodeId, e.getMessage());
                    String errorOutput = "上一个节点[" + nodeId + "]执行失败: " + e.getMessage()
                            + "\n请基于此信息继续处理: " + input;
                    return Map.of("output", errorOutput, "messages", "节点[" + nodeId + "]执行失败: " + e.getMessage());
                }
            }));
        }

        // 添加边：START → 第一个节点 → ... → 最后一个节点 → END
        workflow.addEdge(START, nodes.get(0).getId());
        for (int i = 0; i < nodes.size() - 1; i++) {
            workflow.addEdge(nodes.get(i).getId(), nodes.get(i + 1).getId());
        }
        workflow.addEdge(nodes.get(nodes.size() - 1).getId(), END);

        return workflow;
    }
}
