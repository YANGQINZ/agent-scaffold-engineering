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

    /** 内联指令（不依赖 AgentRegistry 查找） */
    private String instruction;

    /** MCP服务器配置（JSONB） */
    private String mcpServers;

    /** 启用工具配置（JSONB） */
    private String enableTools;

    /** 输出键名 — 后续 Agent instruction 中通过 {outputKey} 模板变量引用 */
    private String outputKey;

    /** 排序序号 */
    private Integer sortOrder;

}
