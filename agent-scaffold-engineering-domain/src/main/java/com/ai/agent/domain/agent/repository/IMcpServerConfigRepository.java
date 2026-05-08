package com.ai.agent.domain.agent.repository;

import com.ai.agent.domain.agent.model.entity.McpServerConfig;

import java.util.List;
import java.util.Optional;

/**
 * MCP Server 配置仓储接口
 *
 * 管理持久化的 MCP Server 配置，支持跨 Agent 复用。
 */
public interface IMcpServerConfigRepository {

    /**
     * 保存 MCP 配置
     *
     * @param config MCP 配置（含 name, transport 等字段）
     * @param description 配置描述
     * @return 保存后的配置（含生成的 id）
     */
    McpServerConfigEntry save(McpServerConfig config, String description);

    /**
     * 更新 MCP 配置
     *
     * @param id 配置 ID
     * @param config 更新后的 MCP 配置
     * @param description 配置描述
     */
    void update(Long id, McpServerConfig config, String description);

    /**
     * 根据 ID 删除 MCP 配置
     *
     * @param id 配置 ID
     */
    void deleteById(Long id);

    /**
     * 根据 ID 查找 MCP 配置
     *
     * @param id 配置 ID
     * @return MCP 配置条目
     */
    Optional<McpServerConfigEntry> findById(Long id);

    /**
     * 根据名称查找 MCP 配置
     *
     * @param name 配置名称
     * @return MCP 配置条目
     */
    Optional<McpServerConfigEntry> findByName(String name);

    /**
     * 查询所有 MCP 配置
     *
     * @return MCP 配置列表
     */
    List<McpServerConfigEntry> findAll();

    /**
     * MCP 配置持久化实体（包含 id 和 description）
     */
    record McpServerConfigEntry(
            Long id,
            String name,
            String transport,
            String command,
            List<String> args,
            String url,
            java.util.Map<String, String> headers,
            String description
    ) {
        /**
         * 转换为 McpServerConfig 值对象
         */
        public McpServerConfig toMcpServerConfig() {
            return McpServerConfig.builder()
                    .name(name)
                    .transport(transport)
                    .command(command)
                    .args(args)
                    .url(url)
                    .headers(headers)
                    .build();
        }
    }

}
