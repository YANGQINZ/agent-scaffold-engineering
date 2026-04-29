package com.ai.agent.api.model.agent;

import lombok.*;

/**
 * 工作流节点DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeDTO {

    /** 节点ID */
    private String id;

    /** 节点绑定的Agent ID */
    private String agentId;

    /** 引用的React Agent ID */
    private String reactAgentId;

    /** 下一个节点ID */
    private String next;

    /** 是否启用RAG */
    private Boolean ragEnabled;

    /** 关联的知识库ID */
    private String knowledgeBaseId;

}
