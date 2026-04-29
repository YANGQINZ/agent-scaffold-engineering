package com.ai.agent.api.model.agent;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 配置DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfigDTO {

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
