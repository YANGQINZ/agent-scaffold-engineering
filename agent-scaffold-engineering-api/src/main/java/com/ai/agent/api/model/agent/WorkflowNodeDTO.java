package com.ai.agent.api.model.agent;

import lombok.*;

import java.util.List;
import java.util.Map;

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

    /** 内联提示词 */
    private String instruction;

    /** 子引擎类型 */
    private String subEngine;

    /** 节点级 MCP 工具配置 */
    private List<McpServerConfigDTO> mcpServers;

}
