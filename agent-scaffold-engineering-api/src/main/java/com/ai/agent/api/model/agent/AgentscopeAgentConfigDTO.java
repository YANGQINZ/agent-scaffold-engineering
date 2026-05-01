package com.ai.agent.api.model.agent;

import lombok.*;

import java.util.List;

/**
 * AgentScope Agent配置DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentscopeAgentConfigDTO {

    /** Agent ID */
    private String agentId;

    /** Agent 指令（内联定义时直接传入，避免依赖 AgentRegistry 查找） */
    private String instruction;

    /** MCP Server 配置列表 */
    private List<McpServerConfigDTO> mcpServers;

    /** 启用的工具列表 */
    private List<String> enableTools;

}
