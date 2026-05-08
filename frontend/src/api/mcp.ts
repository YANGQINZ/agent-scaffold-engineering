/**
 * MCP Server 配置管理 API
 * 对应后端 McpServerConfigController
 */
import { apiClient, type ApiResponse } from './client';

// ═══════════════════════════════════════════════════════════
// 类型定义
// ═══════════════════════════════════════════════════════════

/** MCP 配置信息 */
export interface McpConfigItem {
  /** 主键ID */
  id: number;
  /** 配置名称 */
  name: string;
  /** 传输协议 */
  transport: string;
  /** stdio 命令 */
  command?: string;
  /** 命令参数 */
  args?: string[];
  /** HTTP URL */
  url?: string;
  /** HTTP 请求头 */
  headers?: Record<string, string>;
  /** 描述 */
  description?: string;
}

// ═══════════════════════════════════════════════════════════
// API 函数
// ═══════════════════════════════════════════════════════════

/**
 * 查询所有 MCP 配置
 * GET /mcp
 */
export async function listMcpConfigs(): Promise<McpConfigItem[]> {
  const res = await apiClient.get<ApiResponse<McpConfigItem[]>>('/mcp');
  return res.data.data;
}

/**
 * 创建 MCP 配置
 * POST /mcp
 */
export async function createMcpConfig(data: Omit<McpConfigItem, 'id'>): Promise<McpConfigItem> {
  const res = await apiClient.post<ApiResponse<McpConfigItem>>('/mcp', data);
  return res.data.data;
}

/**
 * 更新 MCP 配置
 * PUT /mcp/{id}
 */
export async function updateMcpConfig(id: number, data: Omit<McpConfigItem, 'id'>): Promise<void> {
  await apiClient.put(`/mcp/${id}`, data);
}

/**
 * 删除 MCP 配置
 * DELETE /mcp/{id}
 */
export async function deleteMcpConfig(id: number): Promise<void> {
  await apiClient.delete(`/mcp/${id}`);
}
