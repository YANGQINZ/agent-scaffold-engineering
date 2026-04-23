package com.ai.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Workflow节点实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNode {

    /** 节点ID */
    private String id;

    /** 节点对应的AgentID */
    private String agentId;

    /** 节点对应的ReAct AgentID */
    private String reactAgentId;

    /** 下一个节点ID */
    private String next;

}
