package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体定义持久化对象
 */
@Data
public class AgentDefinitionPO {

    /** 主键ID */
    private Long id;

    /** 智能体ID */
    private String agentId;

    /** 智能体名称 */
    private String name;

    /** 执行引擎 */
    private String engine;

    /** 系统指令 */
    private String instruction;

    /** 模型配置（JSONB） */
    private String modelConfig;

    /** MCP服务器配置（JSONB） */
    private String mcpServers;

    /** 图起始节点 */
    private String graphStart;

    /** AgentScope流水线类型 */
    private String agentscopePipelineType;

    /** 子引擎配置（JSONB） */
    private String subEngines;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
