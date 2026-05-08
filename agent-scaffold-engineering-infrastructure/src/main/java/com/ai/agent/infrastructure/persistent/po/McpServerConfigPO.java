package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP Server 配置持久化对象
 */
@Data
public class McpServerConfigPO {

    /** 主键ID */
    private Long id;

    /** 配置名称（唯一） */
    private String name;

    /** 传输协议：stdio / sse / streamableHttp */
    private String transport;

    /** stdio 可执行命令 */
    private String command;

    /** 命令参数（JSON 数组） */
    private String args;

    /** HTTP 端点 URL */
    private String url;

    /** HTTP 请求头（JSON 对象） */
    private String headers;

    /** 配置描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
