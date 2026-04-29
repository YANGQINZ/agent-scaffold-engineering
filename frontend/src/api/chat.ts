/**
 * 对话相关 API
 * 对应后端 ChatController + SessionController
 */
import { apiClient, fetchSSE, type ApiResponse } from './client';

// ═══════════════════════════════════════════════════════════
// 类型定义
// ═══════════════════════════════════════════════════════════

/** 对话模式 */
export type ChatMode = 'SIMPLE' | 'MULTI_TURN' | 'AGENT';

/** 引擎类型 */
export type EngineType = 'CHAT' | 'GRAPH' | 'AGENTSCOPE' | 'HYBRID';

/** 对话请求参数 */
export interface ChatRequest {
  /** 用户ID */
  userId?: string;
  /** 会话ID（续聊时传入） */
  sessionId?: string;
  /** 用户输入 */
  query: string;
  /** 对话模式 */
  mode?: ChatMode;
  /** 引擎类型 */
  engine?: EngineType;
  /** 是否启用 RAG */
  ragEnabled?: boolean;
  /** 知识库ID */
  knowledgeBaseId?: string;
  /** 是否启用思考过程输出 */
  enableThinking?: boolean;
  /** 指定目标 Agent ID */
  agentId?: string;
}

/** RAG 来源信息 */
export interface Source {
  docName: string;
  chunkContent: string;
  score: number;
}

/** 对话响应 */
export interface ChatResponse {
  answer: string;
  sessionId: string;
  ragDegraded?: boolean;
  thinkingContent?: string;
  metadata?: Record<string, unknown>;
  sources?: Source[];
}

/** SSE 流式事件类型 */
export type StreamEventType =
  | 'TEXT_DELTA'
  | 'THINKING'
  | 'NODE_START'
  | 'NODE_END'
  | 'RAG_RETRIEVE'
  | 'DONE';

/** SSE 流式事件 */
export interface StreamEvent {
  /** 事件类型 */
  type: StreamEventType;
  /** 事件数据（可能是字符串或对象） */
  data: Record<string, unknown>;
  /** 会话ID */
  sessionId?: string;
}

/** 会话信息 */
export interface ChatSessionInfo {
  sessionId: string;
  userId: string;
  agentId?: string;
  mode: string;
  engine: string;
  ragEnabled?: boolean;
  knowledgeBaseId?: string;
  createdAt: string;
  lastActiveAt: string;
}

/** 消息信息 */
export interface ChatMessageInfo {
  messageId: string;
  sessionId: string;
  role: string;
  content: string;
  tokenCount?: number;
  createdAt: string;
}

// ═══════════════════════════════════════════════════════════
// API 函数
// ═══════════════════════════════════════════════════════════

/**
 * 同步对话
 * POST /chat
 */
export async function sendChat(params: ChatRequest): Promise<ChatResponse> {
  const res = await apiClient.post<ApiResponse<ChatResponse>>('/chat', params);
  return res.data.data;
}

/**
 * 流式对话
 * POST /chat/stream — 返回 AbortController 用于取消
 */
export function streamChat(
  params: ChatRequest,
  onEvent: (event: StreamEvent) => void,
): AbortController {
  return fetchSSE<StreamEvent>('/chat/stream', params as unknown as Record<string, unknown>, onEvent);
}

/**
 * 删除会话
 * DELETE /chat/session/{sessionId}
 */
export async function deleteSession(sessionId: string): Promise<void> {
  await apiClient.delete(`/chat/session/${sessionId}`);
}

/**
 * 查询会话列表
 * GET /chat/sessions?agentId={agentId}
 */
export async function listSessions(
  agentId?: string,
): Promise<ChatSessionInfo[]> {
  const res = await apiClient.get<ApiResponse<ChatSessionInfo[]>>(
    '/chat/sessions',
    { params: agentId ? { agentId } : undefined },
  );
  return res.data.data;
}

/**
 * 查询会话消息历史
 * GET /chat/sessions/{sessionId}/messages
 */
export async function getSessionMessages(
  sessionId: string,
): Promise<ChatMessageInfo[]> {
  const res = await apiClient.get<ApiResponse<ChatMessageInfo[]>>(
    `/chat/sessions/${sessionId}/messages`,
  );
  return res.data.data;
}
