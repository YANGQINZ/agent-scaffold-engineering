package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;

/**
 * 图边持久化对象
 */
@Data
public class AgentGraphEdgePO {

    /** 主键ID */
    private Long id;

    /** 智能体定义ID */
    private Long agentDefinitionId;

    /** 起始节点 */
    private String fromNode;

    /** 目标节点 */
    private String toNode;

    /** 条件表达式 */
    private String conditionExpr;

    /** 排序序号 */
    private Integer sortOrder;

}
