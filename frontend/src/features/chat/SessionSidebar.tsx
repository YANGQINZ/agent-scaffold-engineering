/**
 * 会话历史侧边栏
 * 左侧 220px 宽侧栏，按日期分组显示会话列表
 * 支持切换会话、新建对话
 */
import { memo, useCallback, useEffect, useMemo } from 'react';
import { Plus, MessageSquare, Bot } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';
import { useChatStore, type SessionInfo } from '@/stores/chat';
import { useCanvasStore } from '@/stores/canvas';
import { getSessionMessages, listSessions } from '@/api/chat';
import { cn } from '@/lib/utils';

/** 日期分组标签 */
type DateGroup = '今天' | '昨天' | '更早';

/** 将 ISO 时间字符串分类到日期组 */
function getDateGroup(dateStr: string): DateGroup {
  const date = new Date(dateStr);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const yesterday = new Date(today.getTime() - 86400000);

  if (date >= today) return '今天';
  if (date >= yesterday) return '昨天';
  return '更早';
}

/** 按日期分组会话列表 */
function groupByDate(sessions: SessionInfo[]): Record<DateGroup, SessionInfo[]> {
  const groups: Record<DateGroup, SessionInfo[]> = {
    今天: [],
    昨天: [],
    更早: [],
  };
  for (const session of sessions) {
    const group = getDateGroup(session.lastActiveAt);
    groups[group].push(session);
  }
  return groups;
}

function SessionSidebar() {
  const selectedAgentId = useChatStore((s) => s.selectedAgentId);
  const activeSessionId = useChatStore((s) => s.activeSessionId);
  const sessions = useChatStore((s) => s.sessions);
  const setActiveSessionId = useChatStore((s) => s.setActiveSessionId);
  const clearMessages = useChatStore((s) => s.clearMessages);
  const addMessage = useChatStore((s) => s.addMessage);
  const setSessions = useChatStore((s) => s.setSessions);
  const agents = useCanvasStore((s) => s.agents);

  /** 当前 Agent 的名称 */
  const activeAgent = useMemo(
    () => agents.find((a) => a.agentId === selectedAgentId),
    [agents, selectedAgentId],
  );

  /** 当前 Agent 的会话列表 */
  const currentSessions = useMemo(
    () => (selectedAgentId ? sessions[selectedAgentId] ?? [] : []),
    [sessions, selectedAgentId],
  );

  /** 按日期分组 */
  const grouped = useMemo(() => groupByDate(currentSessions), [currentSessions]);

  /** 加载指定 Agent 的会话列表 */
  const loadSessions = useCallback(
    async (agentId: string) => {
      try {
        const res = await listSessions(agentId);
        setSessions(
          agentId,
          res.map((s) => ({
            sessionId: s.sessionId,
            title: s.sessionId.slice(0, 8),
            lastActiveAt: s.lastActiveAt,
            messageCount: 0,
          })),
        );
      } catch {
        setSessions(agentId, []);
      }
    },
    [setSessions],
  );

  /** 当 selectedAgentId 变化时加载会话列表 */
  useEffect(() => {
    if (selectedAgentId && !sessions[selectedAgentId]) {
      loadSessions(selectedAgentId);
    }
  }, [selectedAgentId, sessions, loadSessions]);

  /** 点击会话：加载该会话的历史消息 */
  const handleSelectSession = useCallback(
    async (sessionId: string) => {
      if (sessionId === activeSessionId) return;
      setActiveSessionId(sessionId);
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
    [activeSessionId, setActiveSessionId, clearMessages, addMessage],
  );

  /** 新建对话 */
  const handleNewChat = useCallback(() => {
    setActiveSessionId(null);
    clearMessages();
  }, [setActiveSessionId, clearMessages]);

  return (
    <div className="flex w-[220px] shrink-0 flex-col border-r border-gray-200 bg-gray-50">
      {/* ── 头部 ── */}
      <div className="flex items-center justify-between px-3 py-3">
        <h3 className="text-xs font-semibold text-gray-500">会话历史</h3>
        <Button variant="ghost" size="xs" onClick={handleNewChat}>
          <Plus className="size-3" />
          新对话
        </Button>
      </div>

      {/* ── 当前 Agent 上下文 ── */}
      {activeAgent && (
        <div className="mx-3 mb-2 flex items-center gap-1.5 rounded-md bg-indigo-50 px-2 py-1.5">
          <Bot className="size-3.5 text-indigo-500" />
          <span className="truncate text-xs font-medium text-indigo-700">
            {activeAgent.name}
          </span>
        </div>
      )}

      {/* ── 会话列表 ── */}
      <ScrollArea className="flex-1">
        <div className="px-2 pb-2">
          {(['今天', '昨天', '更早'] as DateGroup[]).map((group) => {
            const items = grouped[group];
            if (!items || items.length === 0) return null;
            return (
              <div key={group} className="mb-2">
                <p className="mb-1 px-2 text-[11px] font-medium text-gray-400">
                  {group}
                </p>
                {items.map((session) => {
                  const isActive = session.sessionId === activeSessionId;
                  return (
                    <button
                      key={session.sessionId}
                      onClick={() => handleSelectSession(session.sessionId)}
                      className={cn(
                        'flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-xs transition-colors',
                        isActive
                          ? 'border-l-2 border-indigo-500 bg-indigo-50 text-indigo-700'
                          : 'text-gray-600 hover:bg-gray-100',
                      )}
                    >
                      <MessageSquare className="size-3.5 shrink-0 text-gray-400" />
                      <span className="truncate">{session.title}</span>
                    </button>
                  );
                })}
              </div>
            );
          })}

          {currentSessions.length === 0 && (
            <p className="px-2 py-6 text-center text-[11px] text-gray-400">
              暂无会话记录
            </p>
          )}
        </div>
      </ScrollArea>

      {/* ── 底部提示 ── */}
      <div className="border-t border-gray-200 px-3 py-2">
        <p className="text-[10px] text-gray-400">
          切换 Agent → 历史列表跟随切换
        </p>
      </div>
    </div>
  );
}

export default memo(SessionSidebar);
