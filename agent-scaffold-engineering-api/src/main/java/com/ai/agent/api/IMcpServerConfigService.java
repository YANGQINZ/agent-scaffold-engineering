package com.ai.agent.api;

import com.ai.agent.api.model.agent.McpServerConfigDTO;
import com.ai.agent.types.model.Response;

import java.util.List;

/**
 * MCP Server 配置管理接口
 */
public interface IMcpServerConfigService {

    /**
     * 查询所有 MCP 配置
     */
    Response<List<McpServerConfigDTO>> listMcpConfigs();

    /**
     * 创建 MCP 配置
     */
    Response<McpServerConfigDTO> createMcpConfig(McpServerConfigDTO dto);

    /**
     * 更新 MCP 配置
     */
    Response<Void> updateMcpConfig(Long id, McpServerConfigDTO dto);

    /**
     * 删除 MCP 配置
     */
    Response<Void> deleteMcpConfig(Long id);

}
