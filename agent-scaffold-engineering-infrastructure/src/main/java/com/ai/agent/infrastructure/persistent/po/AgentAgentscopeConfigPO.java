package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;

/**
 * AgentScope配置持久化对象
 */
@Data
public class AgentAgentscopeConfigPO {

    /** 主键ID */
    private Long id;

    /** 智能体定义ID */
    private Long agentDefinitionId;

    /** 配置智能体ID */
    private String configAgentId;

    /** MCP服务器配置（JSONB） */
    private String mcpServers;

    /** 启用工具配置（JSONB） */
    private String enableTools;

    /** 排序序号 */
    private Integer sortOrder;

}
