/**
 * 对话状态管理
 * 消息列表、会话管理、流式状态、节点执行状态
 */
import { create } from 'zustand';
import type { Source } from '@/api/chat';

// ═══════════════════════════════════════════════════════════
// 类型定义
// ═══════════════════════════════════════════════════════════

/** 消息 */
export interface Message {
  /** 消息唯一ID */
  id: string;
  /** 角色 */
  role: 'user' | 'assistant';
  /** 内容 */
  content: string;
  /** 思考过程内容 */
  thinkingContent?: string;
  /** RAG 来源 */
  sources?: Source[];
  /** 时间戳 */
  timestamp: number;
}

/** 会话摘要信息 */
export interface SessionInfo {
  sessionId: string;
  title: string;
  lastActiveAt: string;
  messageCount: number;
}

/** 节点执行状态 */
export interface NodeExecutionStatus {
  nodeId: string;
  status: 'running' | 'done' | 'error';
  duration?: number;
}

// ═══════════════════════════════════════════════════════════
// Store 定义
// ═══════════════════════════════════════════════════════════

interface ChatState {
  /** 当前会话消息列表 */
  messages: Message[];
  /** 会话列表（按 agentId 分组） */
  sessions: Record<string, SessionInfo[]>;
  /** 当前选中的 Agent ID */
  selectedAgentId: string | null;
  /** 当前活跃的会话 ID */
  activeSessionId: string | null;
  /** 是否正在流式输出 */
  isStreaming: boolean;
  /** 节点执行状态列表 */
  nodeExecutionStatus: NodeExecutionStatus[];

  // ─── Actions ───

  /** 添加消息 */
  addMessage: (msg: Message) => void;
  /** 清空当前消息列表 */
  clearMessages: () => void;
  /** 设置某个 Agent 下的会话列表 */
  setSessions: (agentId: string, sessions: SessionInfo[]) => void;
  /** 设置选中的 Agent ID */
  setSelectedAgentId: (id: string | null) => void;
  /** 设置活跃的会话 ID */
  setActiveSessionId: (id: string | null) => void;
  /** 设置流式输出状态 */
  setIsStreaming: (v: boolean) => void;
  /** 追加内容到最后一条助手消息 */
  appendToLastMessage: (content: string) => void;
  /** 设置最后一条助手消息的思考内容 */
  setThinkingContent: (content: string) => void;
  /** 设置最后一条助手消息的 RAG 来源 */
  setSources: (sources: Source[]) => void;
  /** 添加节点执行状态 */
  addNodeStatus: (status: NodeExecutionStatus) => void;
  /** 更新节点执行状态 */
  updateNodeStatus: (
    nodeId: string,
    status: 'running' | 'done' | 'error',
    duration?: number,
  ) => void;
  /** 清空所有节点执行状态 */
  clearNodeStatuses: () => void;
}

export const useChatStore = create<ChatState>((set) => ({
  messages: [],
  sessions: {},
  selectedAgentId: null,
  activeSessionId: null,
  isStreaming: false,
  nodeExecutionStatus: [],

  addMessage: (msg) =>
    set((state) => ({ messages: [...state.messages, msg] })),

  clearMessages: () => set({ messages: [] }),

  setSessions: (agentId, sessions) =>
    set((state) => ({
      sessions: { ...state.sessions, [agentId]: sessions },
    })),

  setSelectedAgentId: (selectedAgentId) => set({ selectedAgentId }),

  setActiveSessionId: (activeSessionId) => set({ activeSessionId }),

  setIsStreaming: (isStreaming) => set({ isStreaming }),

  appendToLastMessage: (content) =>
    set((state) => {
      const messages = [...state.messages];
      const last = messages[messages.length - 1];
      if (last && last.role === 'assistant') {
        messages[messages.length - 1] = {
          ...last,
          content: last.content + content,
        };
      }
      return { messages };
    }),

  setThinkingContent: (content) =>
    set((state) => {
      const messages = [...state.messages];
      const last = messages[messages.length - 1];
      if (last && last.role === 'assistant') {
        messages[messages.length - 1] = { ...last, thinkingContent: content };
      }
      return { messages };
    }),

  setSources: (sources) =>
    set((state) => {
      const messages = [...state.messages];
      const last = messages[messages.length - 1];
      if (last && last.role === 'assistant') {
        messages[messages.length - 1] = { ...last, sources };
      }
      return { messages };
    }),

  addNodeStatus: (status) =>
    set((state) => ({
      nodeExecutionStatus: [...state.nodeExecutionStatus, status],
    })),

  updateNodeStatus: (nodeId, status, duration) =>
    set((state) => ({
      nodeExecutionStatus: state.nodeExecutionStatus.map((s) =>
        s.nodeId === nodeId ? { ...s, status, duration } : s,
      ),
    })),

  clearNodeStatuses: () => set({ nodeExecutionStatus: [] }),
}));
