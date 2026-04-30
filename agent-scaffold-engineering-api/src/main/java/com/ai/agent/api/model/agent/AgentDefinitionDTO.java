package com.ai.agent.api.model.agent;

import com.ai.agent.types.enums.EngineType;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Agent定义DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDefinitionDTO {

    /** Agent唯一标识 */
    private String agentId;

    /** Agent名称 */
    private String name;

    /** 引擎类型 */
    private EngineType engine;

    /** 系统指令 */
    private String instruction;

    /** 模型配置 */
    private ModelConfigDTO modelConfig;

    /** MCP Server 配置列表 */
    private List<McpServerConfigDTO> mcpServers;

    // GRAPH / HYBRID specific

    /** 图起始节点（支持多入口） */
    private List<String> graphStart;

    /** 图节点列表 */
    private List<WorkflowNodeDTO> graphNodes;

    /** 图边列表 */
    private List<GraphEdgeDTO> graphEdges;

    // AGENTSCOPE specific

    /** AgentScope Pipeline 类型 */
    private String agentscopePipelineType;

    /** AgentScope Agent 配置列表 */
    private List<AgentscopeAgentConfigDTO> agentscopeAgents;

    // HYBRID specific

    /** 子节点引擎类型映射 */
    private Map<String, EngineType> subEngines;

}
