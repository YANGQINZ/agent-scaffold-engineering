package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;

/**
 * 工作流节点持久化对象
 */
@Data
public class AgentWorkflowNodePO {

    /** 主键ID */
    private Long id;

    /** 智能体定义ID */
    private Long agentDefinitionId;

    /** 节点ID */
    private String nodeId;

    /** 智能体ID */
    private String agentId;

    /** 是否启用RAG */
    private Boolean ragEnabled;

    /** 知识库ID */
    private String knowledgeBaseId;

    /** 节点指令 */
    private String instruction;

    /** 子引擎类型 */
    private String subEngine;

    /** MCP 服务器配置（JSONB） */
    private String mcpServers;

    /** 排序序号 */
    private Integer sortOrder;

}
