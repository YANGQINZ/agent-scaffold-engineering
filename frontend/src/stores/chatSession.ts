/**
 * 会话管理状态
 * 管理 ChatPage 的会话列表、当前选中会话
 */
import { create } from 'zustand';
import { createSession, getSessionMessages, listAllSessions, type ChatSessionVO } from '../api/chat';
import { useChatStore } from './chat';

interface ChatSessionState {
  sessions: ChatSessionVO[];
  currentSessionId: string | null;
  loading: boolean;

  loadSessions: () => Promise<void>;
  createNewSession: (name?: string) => Promise<string>;
  selectSession: (sessionId: string) => Promise<void>;
  clearCurrentSession: () => void;
}

export const useChatSessionStore = create<ChatSessionState>((set) => ({
  sessions: [],
  currentSessionId: null,
  loading: false,

  loadSessions: async () => {
    set({ loading: true });
    try {
      const sessions = await listAllSessions();
      set({ sessions: Array.isArray(sessions) ? sessions : [], loading: false });
    } catch {
      set({ loading: false });
    }
  },

  createNewSession: async (name?: string) => {
    const sessionId = await createSession({
      name,
      mode: 'MULTI_TURN',
      engine: 'CHAT',
    });
    set((state) => ({
      currentSessionId: sessionId,
      sessions: [
        { sessionId, name: name || '新会话', createdAt: new Date().toISOString() },
        ...state.sessions,
      ],
    }));
    return sessionId;
  },

  selectSession: async (sessionId: string) => {
    set({ currentSessionId: sessionId });
    // 切换会话时加载历史消息
    const { clearMessages, addMessage } = useChatStore.getState();
    clearMessages();
    try {
      const msgs = await getSessionMessages(sessionId);
      for (const msg of msgs) {
        addMessage({
          id: msg.messageId,
          role: msg.role === 'user' ? 'user' : 'assistant',
          content: msg.content,
          timestamp: new Date(msg.createdAt).getTime(),
        });
      }
    } catch {
      // 加载失败则保持空消息
    }
  },

  clearCurrentSession: () => {
    set({ currentSessionId: null });
  },
}));
