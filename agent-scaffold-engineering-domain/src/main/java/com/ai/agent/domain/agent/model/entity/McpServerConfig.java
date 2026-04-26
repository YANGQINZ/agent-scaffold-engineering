package com.ai.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 配置值对象
 *
 * 定义 MCP Server 的连接方式（stdio/sse/streamableHttp）及相关参数。
 * 支持 YAML 声明式配置，可按 Agent 级别声明。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfig {

    /** MCP Server 名称（唯一标识） */
    private String name;

    /** 传输类型：stdio / sse / streamableHttp */
    private String transport;

    /** stdio 传输的可执行命令 */
    private String command;

    /** stdio 传输的命令参数 */
    private List<String> args;

    /** sse/streamableHttp 传输的 URL */
    private String url;

    /** HTTP 请求头（用于认证等） */
    private Map<String, String> headers;

}
