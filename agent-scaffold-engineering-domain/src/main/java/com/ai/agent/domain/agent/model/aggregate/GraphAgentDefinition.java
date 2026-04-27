package com.ai.agent.domain.agent.model.aggregate;

import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.types.enums.EngineType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * GRAPH 引擎 Agent 定义 — Spring AI Alibaba StateGraph 编排
 *
 * 持有 Graph 编排特有字段：起始节点、节点列表、边列表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class GraphAgentDefinition extends AgentDefinition {

    /** Graph编排 - 起始节点ID */
    private String graphStart;

    /** Graph编排 - 节点列表 */
    private List<WorkflowNode> graphNodes = List.of();

    /** Graph编排 - 边列表 */
    private List<GraphEdge> graphEdges = List.of();

    @Override
    public EngineType getEngine() {
        return EngineType.GRAPH;
    }

    @Override
    public void setEngine(EngineType engine) {
        if (engine != EngineType.GRAPH) {
            throw new IllegalArgumentException("GraphAgentDefinition 的 engine 必须为 GRAPH");
        }
        super.setEngine(engine);
    }

}
