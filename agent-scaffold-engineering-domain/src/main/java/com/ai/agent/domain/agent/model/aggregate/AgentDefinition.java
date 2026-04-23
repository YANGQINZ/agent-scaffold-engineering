package com.ai.agent.domain.agent.model.aggregate;

import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.ToolConfig;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.types.enums.AgentMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent定义聚合根
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDefinition {

    /** Agent唯一标识 */
    private String agentId;

    /** Agent名称 */
    private String name;

    /** Agent模式 */
    private AgentMode mode;

    /** 系统指令 */
    private String instruction;

    /** 模型配置 */
    @Builder.Default
    private ModelConfig modelConfig = new ModelConfig();

    /** 工具配置列表 */
    @Builder.Default
    private List<ToolConfig> tools = List.of();

    /** Workflow编排 - 入口节点ID */
    private String workflowEntry;

    /** Workflow编排 - 节点列表 */
    @Builder.Default
    private List<WorkflowNode> workflowNodes = List.of();

    /** Graph编排 - 起始节点ID */
    private String graphStart;

    /** Graph编排 - 节点列表 */
    @Builder.Default
    private List<WorkflowNode> graphNodes = List.of();

    /** Graph编排 - 边列表 */
    @Builder.Default
    private List<GraphEdge> graphEdges = List.of();

}
