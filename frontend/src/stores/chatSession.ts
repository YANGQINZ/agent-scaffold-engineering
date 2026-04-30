/**
 * 会话管理状态
 * 管理 ChatPage 的会话列表、当前选中会话
 */
import { create } from 'zustand';
import { createSession, listAllSessions, type ChatSessionVO } from '../api/chat';

interface ChatSessionState {
  sessions: ChatSessionVO[];
  currentSessionId: string | null;
  loading: boolean;

  loadSessions: () => Promise<void>;
  createNewSession: (name?: string) => Promise<string>;
  selectSession: (sessionId: string) => void;
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
      set({ sessions, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  createNewSession: async (name?: string) => {
    const sessionId = await createSession(name);
    set((state) => ({
      currentSessionId: sessionId,
      sessions: [
        { sessionId, name: name || '新会话', createdAt: new Date().toISOString() },
        ...state.sessions,
      ],
    }));
    return sessionId;
  },

  selectSession: (sessionId: string) => {
    set({ currentSessionId: sessionId });
  },

  clearCurrentSession: () => {
    set({ currentSessionId: null });
  },
}));
