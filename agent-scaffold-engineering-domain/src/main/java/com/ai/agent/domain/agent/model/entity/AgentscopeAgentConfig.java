package com.ai.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AgentScope Agent 级别配置值对象
 *
 * 定义 AgentScope 引擎中单个 Agent 的配置，
 * 包括关联的 AgentDefinition ID、MCP 工具和启用的工具列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentscopeAgentConfig {

    /** 关联的 AgentDefinition ID */
    private String agentId;

    /** Agent 指令（内联定义时直接传入，避免依赖 AgentRegistry 查找） */
    private String instruction;

    /** Agent 级别的 MCP Server 配置 */
    private List<McpServerConfig> mcpServers = List.of();

    /** 启用的工具名称列表（为空时启用全部工具） */
    private List<String> enableTools = List.of();

    /**
     * 输出键名 — 当前 Agent 的执行结果存入 OverAllState 的 key
     * 后续 Agent 的 instruction 中可通过 {outputKey} 模板变量引用
     * 示例：agentA 配置 outputKey="research_result"，agentB 的 instruction 写 "基于以下内容摘要：{research_result}"
     * 为空时自动生成 "agent_{index}"
     */
    private String outputKey;

}
