package com.ai.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    /** 是否启用 RAG 增强检索 */
    @Builder.Default
    private Boolean ragEnabled = false;

    /** 关联知识库 ID（ragEnabled=true 时使用） */
    private String knowledgeBaseId;

    // --- 新增字段 ---
    /** 内联提示词（优先于 agentId 引用的子代理 instruction） */
    private String instruction;

    /** 子引擎类型: "GRAPH"(默认) / "AGENTSCOPE" */
    private String subEngine;

    /** 节点级 MCP 工具配置 */
    private List<McpServerConfig> mcpServers;

}
