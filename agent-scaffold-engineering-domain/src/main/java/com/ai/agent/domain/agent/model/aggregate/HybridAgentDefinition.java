package com.ai.agent.domain.agent.model.aggregate;

import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.types.enums.EngineType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * HYBRID 引擎 Agent 定义 — 外层 Graph 编排 + 子节点引擎委托
 *
 * 持有 Graph 编排字段（与 GraphAgentDefinition 重复，但保持扁平访问路径）
 * 以及 Hybrid 特有的 subEngines 映射。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class HybridAgentDefinition extends AgentDefinition {

    /** Graph编排 - 起始节点ID */
    private String graphStart;

    /** Graph编排 - 节点列表 */
    private List<WorkflowNode> graphNodes = List.of();

    /** Graph编排 - 边列表 */
    private List<GraphEdge> graphEdges = List.of();

    /** Hybrid 模式子节点引擎映射（nodeId → EngineType） */
    private Map<String, EngineType> subEngines = Map.of();

    @Override
    public EngineType getEngine() {
        return EngineType.HYBRID;
    }

    @Override
    public void setEngine(EngineType engine) {
        if (engine != EngineType.HYBRID) {
            throw new IllegalArgumentException("HybridAgentDefinition 的 engine 必须为 HYBRID");
        }
        super.setEngine(engine);
    }

    /**
     * 获取指定子节点的引擎类型
     *
     * @param nodeId 节点ID
     * @return 引擎类型，未声明时默认 GRAPH
     */
    public EngineType getSubEngine(String nodeId) {
        return subEngines != null && subEngines.containsKey(nodeId)
                ? subEngines.get(nodeId)
                : EngineType.GRAPH;
    }

}
