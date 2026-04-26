package com.ai.agent.domain.agent.model.aggregate;

import com.ai.agent.domain.agent.model.entity.AgentscopeAgentConfig;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import com.ai.agent.domain.agent.model.entity.ToolConfig;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.types.enums.AgentMode;
import com.ai.agent.types.enums.EngineType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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

    // ─── 双引擎扩展字段 ─────────────────────────────────

    /** 引擎类型（替代 mode 字段） */
    @Builder.Default
    private EngineType engine = EngineType.GRAPH;

    /** Hybrid 模式子节点引擎映射（nodeId → EngineType） */
    @Builder.Default
    private Map<String, EngineType> subEngines = Map.of();

    /** AgentScope Agent 配置列表 */
    @Builder.Default
    private List<AgentscopeAgentConfig> agentscopeAgents = List.of();

    /** AgentScope Pipeline 类型：sequential / fanout */
    @Builder.Default
    private String agentscopePipelineType = "sequential";

    /** MCP Server 配置列表（跨引擎通用） */
    @Builder.Default
    private List<McpServerConfig> mcpServers = List.of();

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
