/**
 * Agent 定义 CRUD API
 * 对应后端 AgentController
 */
import { apiClient, type ApiResponse } from './client';
import type { EngineType } from './chat';

// ═══════════════════════════════════════════════════════════
// 类型定义 — 对应后端 AgentDefinitionDTO 及嵌套 DTO
// ═══════════════════════════════════════════════════════════

/** 模型配置 */
export interface ModelConfig {
  /** 模型名称 */
  name?: string;
  /** 温度参数 */
  temperature?: number;
  /** 最大 Token 数 */
  maxTokens?: number;
}

/** MCP Server 配置 */
export interface McpServerConfig {
  /** MCP Server 名称（唯一标识） */
  name: string;
  /** 传输类型：stdio / sse / streamableHttp */
  transport: string;
  /** stdio 传输的可执行命令 */
  command?: string;
  /** stdio 传输的命令参数 */
  args?: string[];
  /** sse/streamableHttp 传输的 URL */
  url?: string;
  /** HTTP 请求头（用于认证等） */
  headers?: Record<string, string>;
}

/** 工作流节点 */
export interface WorkflowNode {
  /** 节点ID */
  id: string;
  /** 节点绑定的 Agent ID */
  agentId?: string;
  /** 引用的 React Agent ID */
  reactAgentId?: string;
  /** 下一个节点ID */
  next?: string;
  /** 是否启用 RAG */
  ragEnabled?: boolean;
  /** 关联的知识库ID */
  knowledgeBaseId?: string;
  /** 节点指令 */
  instruction?: string;
  /** 子引擎类型 */
  subEngine?: 'GRAPH' | 'AGENTSCOPE';
  /** MCP Server 配置列表 */
  mcpServers?: McpServerConfig[];
}

/** 图边 */
export interface GraphEdge {
  /** 起始节点 */
  from: string;
  /** 目标节点 */
  to: string;
  /** 条件表达式 */
  condition?: string;
}

/** AgentScope Agent 配置 */
export interface AgentscopeAgentConfig {
  /** Agent ID */
  agentId: string;
  /** MCP Server 配置列表 */
  mcpServers?: McpServerConfig[];
  /** 启用的工具列表 */
  enableTools?: string[];
}

/** Agent 定义 — 对应后端 AgentDefinitionDTO */
export interface AgentDefinition {
  /** Agent 唯一标识 */
  agentId: string;
  /** Agent 名称 */
  name: string;
  /** 引擎类型 */
  engine: EngineType;
  /** 系统指令 */
  instruction?: string;
  /** 模型配置 */
  modelConfig?: ModelConfig;
  /** MCP Server 配置列表 */
  mcpServers?: McpServerConfig[];

  // GRAPH / HYBRID 专用
  /** 图起始节点 */
  graphStart?: string[];
  /** 图节点列表 */
  graphNodes?: WorkflowNode[];
  /** 图边列表 */
  graphEdges?: GraphEdge[];

  // AGENTSCOPE 专用
  /** AgentScope Pipeline 类型 */
  agentscopePipelineType?: string;
  /** AgentScope Agent 配置列表 */
  agentscopeAgents?: AgentscopeAgentConfig[];

  // HYBRID 专用
  /** 子节点引擎类型映射 */
  subEngines?: Record<string, EngineType>;
}

// ═══════════════════════════════════════════════════════════
// API 函数
// ═══════════════════════════════════════════════════════════

/**
 * 查询所有 Agent 定义
 * GET /agents
 */
export async function listAgents(): Promise<AgentDefinition[]> {
  const res = await apiClient.get<ApiResponse<AgentDefinition[]>>('/agents');
  return res.data.data;
}

/**
 * 根据 agentId 查询 Agent 定义
 * GET /agents/{agentId}
 */
export async function getAgent(
  agentId: string,
): Promise<AgentDefinition> {
  const res = await apiClient.get<ApiResponse<AgentDefinition>>(
    `/agents/${agentId}`,
  );
  return res.data.data;
}

/**
 * 创建 Agent 定义
 * POST /agents
 */
export async function createAgent(
  data: Omit<AgentDefinition, 'agentId'> & { agentId?: string },
): Promise<AgentDefinition> {
  const res = await apiClient.post<ApiResponse<AgentDefinition>>(
    '/agents',
    data,
  );
  return res.data.data;
}

/**
 * 更新 Agent 定义
 * PUT /agents/{agentId}
 */
export async function updateAgent(
  agentId: string,
  data: AgentDefinition,
): Promise<AgentDefinition> {
  const res = await apiClient.put<ApiResponse<AgentDefinition>>(
    `/agents/${agentId}`,
    data,
  );
  return res.data.data;
}

/**
 * 删除 Agent 定义
 * DELETE /agents/{agentId}
 */
export async function deleteAgent(agentId: string): Promise<void> {
  await apiClient.delete(`/agents/${agentId}`);
}

/**
 * 保存或更新 Agent 定义
 * 如果 agentId 存在则更新，否则创建
 */
export async function saveOrUpdateAgent(
  agentId: string | null,
  data: Omit<AgentDefinition, 'agentId'>,
): Promise<AgentDefinition> {
  if (agentId) {
    return updateAgent(agentId, { ...data, agentId } as AgentDefinition);
  }
  return createAgent(data);
}
