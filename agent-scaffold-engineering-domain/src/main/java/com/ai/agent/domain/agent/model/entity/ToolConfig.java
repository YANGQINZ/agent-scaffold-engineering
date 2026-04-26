package com.ai.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具配置实体
 *
 * @deprecated 已被 MCP 工具协议替代，请使用 McpServerConfig + McpToolProvider。
 *             短期保留以兼容旧 YAML，优先使用 MCP 协议调用。
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolConfig {

    /** 工具名称 */
    private String name;

    /** 工具类型 */
    private String type;

    /** 工具描述 */
    private String description;

    /** 工具实现类全限定名 */
    private String className;

}
