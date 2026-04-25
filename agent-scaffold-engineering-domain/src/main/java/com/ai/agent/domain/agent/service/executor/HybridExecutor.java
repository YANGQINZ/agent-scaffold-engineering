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
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Hybrid混合执行器 — 基于Spring AI Alibaba StateGraph实现外层Workflow编排，
 * 内层节点可嵌入ReactAgent（CompiledGraph作为子图节点）实现深度推理
 */
@Slf4j
@Service
public class HybridExecutor extends AbstractGraphExecutor {

    public HybridExecutor(ChatModel chatModel, ReactExecutor reactExecutor, AgentRegistry agentRegistry) {
        super(chatModel, reactExecutor, agentRegistry);
    }

    /**
     * 同步执行Hybrid编排
     * 构建StateGraph：外层Workflow节点，含reactAgentId的节点将ReactAgent编译为CompiledGraph
     * 作为子图节点嵌入，否则按普通Agent节点执行
     */
    public ChatResponse execute(AgentDefinition agentDef, ChatRequest request) {
        log.info("HybridExecutor执行: agentId={}, 节点数={}", agentDef.getAgentId(), agentDef.getWorkflowNodes().size());

        List<WorkflowNode> nodes = agentDef.getWorkflowNodes();
        if (nodes == null || nodes.isEmpty()) {
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Hybrid Workflow节点列表为空: agentId=" + agentDef.getAgentId());
        }

        try {
            StateGraph workflow = buildHybridGraph(agentDef, nodes, request);
            CompiledGraph compiled = compileGraph(workflow);
            return invokeGraph(compiled, request, agentDef.getAgentId());
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("HybridExecutor执行失败: agentId={}, error={}", agentDef.getAgentId(), e.getMessage(), e);
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Hybrid编排执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式执行Hybrid编排 — 利用StateGraph.stream()获取节点执行流，
     * 映射为NODE_START/NODE_END事件，从最终节点输出提取答案
     */
    public Flux<StreamEvent> executeStream(AgentDefinition agentDef, ChatRequest request) {
        return Flux.defer(() -> {
            log.info("HybridExecutor流式执行: agentId={}", agentDef.getAgentId());

            List<WorkflowNode> nodes = agentDef.getWorkflowNodes();
            if (nodes == null || nodes.isEmpty()) {
                return Flux.error(new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                        "Hybrid Workflow节点列表为空"));
            }

            try {
                StateGraph workflow = buildHybridGraph(agentDef, nodes, request);
                CompiledGraph compiled = compileGraph(workflow);
                return streamGraph(compiled, request);
            } catch (Exception e) {
                log.error("HybridExecutor流式执行失败: {}", e.getMessage(), e);
                return Flux.error(new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                        "Hybrid编排流式执行失败: " + e.getMessage(), e));
            }
        });
    }

    /**
     * 构建Hybrid StateGraph — 外层Workflow + 内层ReactAgent子图
     * 节点含reactAgentId时，将ReactAgent编译为CompiledGraph作为子图节点嵌入StateGraph
     * 节点含agentId时，按普通子Agent节点执行
     */
    private StateGraph buildHybridGraph(AgentDefinition agentDef, List<WorkflowNode> nodes,
                                         ChatRequest request) throws GraphStateException {
        StateGraph workflow = new StateGraph(defaultKeyStrategyFactory());

        for (WorkflowNode node : nodes) {
            String nodeId = node.getId();
            String reactAgentId = node.getReactAgentId();
            String agentId = node.getAgentId();

            if (reactAgentId != null && !reactAgentId.isBlank()) {
                // 节点含reactAgentId — 构建ReactAgent并编译为CompiledGraph，作为子图节点嵌入
                AgentDefinition reactAgentDef = agentRegistry.get(reactAgentId);
                if (reactAgentDef != null) {
                    CompiledGraph reactSubGraph = buildReactSubGraph(reactAgentDef);
                    workflow.addNode(nodeId, reactSubGraph);
                    log.info("Hybrid节点[{}]嵌入ReactAgent子图: reactAgentId={}", nodeId, reactAgentId);
                } else {
                    // reactAgentId未找到，回退为普通ChatModel节点
                    addChatModelNode(workflow, agentDef, nodeId, request);
                    log.warn("Hybrid节点[{}] reactAgentId未找到，回退ChatModel执行", nodeId);
                }
            } else if (agentId != null && !agentId.isBlank()) {
                // 普通Agent节点执行
                AgentDefinition subAgentDef = agentRegistry.get(agentId);
                if (subAgentDef != null) {
                    workflow.addNode(nodeId, node_async(state -> {
                        String input = state.value("output", request.getQuery());
                        log.info("Hybrid节点执行: nodeId={}, agentId={}", nodeId, agentId);

                        try {
                            ChatResponse nodeResponse = executeSubAgent(subAgentDef, input, request);
                            return Map.of("output", nodeResponse.getAnswer(),
                                    "messages", "节点[" + nodeId + "]子Agent执行成功");
                        } catch (Exception e) {
                            log.warn("Hybrid节点失败: nodeId={}, error={}", nodeId, e.getMessage());
                            String errorOutput = "上一个节点[" + nodeId + "]执行失败: " + e.getMessage()
                                    + "\n请基于此信息继续处理: " + input;
                            return Map.of("output", errorOutput,
                                    "messages", "节点[" + nodeId + "]执行失败: " + e.getMessage());
                        }
                    }));
                } else {
                    addChatModelNode(workflow, agentDef, nodeId, request);
                }
            } else {
                // 无指定Agent，使用ChatModel直接执行
                addChatModelNode(workflow, agentDef, nodeId, request);
            }
        }

        // 添加边：START → 第一个节点 → ... → 最后一个节点 → END
        workflow.addEdge(START, nodes.get(0).getId());
        for (int i = 0; i < nodes.size() - 1; i++) {
            workflow.addEdge(nodes.get(i).getId(), nodes.get(i + 1).getId());
        }
        workflow.addEdge(nodes.get(nodes.size() - 1).getId(), END);

        return workflow;
    }

    /**
     * 构建ReactAgent子图 — 将ReactAgent编译为CompiledGraph，可嵌入StateGraph作为子图节点
     * 绑定AgentDefinition中配置的工具列表
     */
    private CompiledGraph buildReactSubGraph(AgentDefinition reactAgentDef) {
        // 创建工具回调列表
        List<ToolCallback> toolCallbacks = reactExecutor.createToolCallbacks(reactAgentDef.getTools());

        ReactAgent reactAgent = ReactAgent.builder()
                .name(reactAgentDef.getName())
                .model(chatModel)
                .instruction(reactAgentDef.getInstruction())
                .tools(toolCallbacks.toArray(new ToolCallback[0]))
                .enableLogging(true)
                .build();

        return reactAgent.getCompiledGraph();
    }

    /**
     * 添加ChatModel节点到StateGraph
     */
    private void addChatModelNode(StateGraph workflow, AgentDefinition agentDef,
                                   String nodeId, ChatRequest request) throws GraphStateException {
        workflow.addNode(nodeId, node_async(state -> {
            String input = state.value("output", request.getQuery());
            log.info("Hybrid节点执行(ChatModel): nodeId={}", nodeId);

            try {
                String output = executeWithChatModel(agentDef, input);
                return Map.of("output", output, "messages", "节点[" + nodeId + "]ChatModel执行成功");
            } catch (Exception e) {
                log.warn("Hybrid节点失败: nodeId={}, error={}", nodeId, e.getMessage());
                String errorOutput = "上一个节点[" + nodeId + "]执行失败: " + e.getMessage()
                        + "\n请基于此信息继续处理: " + input;
                return Map.of("output", errorOutput,
                        "messages", "节点[" + nodeId + "]执行失败: " + e.getMessage());
            }
        }));
    }
}
