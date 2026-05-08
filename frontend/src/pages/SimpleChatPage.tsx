/**
 * 简单模式多轮对话页面
 * 左侧 240px 会话列表 + 右侧聊天区域（消息列表 + 输入框）
 * 使用 useChatSessionStore 管理会话、useChatStore 管理消息
 */
import { memo, useCallback, useRef, useEffect, useState } from 'react';
import { Bot, User, Plus, Database } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { useChatStore } from '@/stores/chat';
import { useChatSessionStore } from '@/stores/chatSession';
import { useSSE } from '@/hooks/useSSE';
import { listKnowledgeBases, type KnowledgeBase } from '@/api/knowledge';
import { cn } from '@/lib/utils';

/** 生成唯一消息 ID */
const genMsgId = () =>
  `msg_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

function SimpleChatPage() {
  // 会话状态
  const sessions = useChatSessionStore((s) => s.sessions);
  const currentSessionId = useChatSessionStore((s) => s.currentSessionId);
  const loadSessions = useChatSessionStore((s) => s.loadSessions);
  const createNewSession = useChatSessionStore((s) => s.createNewSession);
  const selectSession = useChatSessionStore((s) => s.selectSession);

  // 消息状态
  const messages = useChatStore((s) => s.messages);
  const isStreaming = useChatStore((s) => s.isStreaming);
  const addMessage = useChatStore((s) => s.addMessage);

  const [input, setInput] = useState('');
  const scrollRef = useRef<HTMLDivElement>(null);

  // 知识库选择
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [selectedKB, setSelectedKB] = useState<string>('');

  const { startStream } = useSSE();

  // 当前会话名称
  const currentSession = Array.isArray(sessions)
    ? sessions.find((s) => s.sessionId === currentSessionId)
    : undefined;
  const currentSessionName = currentSession?.name ?? '多轮对话';

  /** 挂载时加载会话列表和知识库列表 */
  useEffect(() => {
    loadSessions();
    listKnowledgeBases().then(setKnowledgeBases).catch(() => {});
  }, [loadSessions]);

  /** 自动滚动 */
  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages]);

  /** 新建会话 */
  const handleNewSession = useCallback(async () => {
    await createNewSession();
  }, [createNewSession]);

  /** 发送消息 */
  const handleSend = useCallback(
    (text?: string) => {
      const content = text ?? input.trim();
      if (!content) return;
      setInput('');

      const userMsgId = genMsgId();
      const assistantMsgId = genMsgId();

      addMessage({ id: userMsgId, role: 'user', content, timestamp: Date.now() });
      addMessage({ id: assistantMsgId, role: 'assistant', content: '', timestamp: Date.now() });

      startStream({
        query: content,
        userId: 'web-user',
        sessionId: currentSessionId ?? undefined,
        mode: 'MULTI_TURN',
        ragEnabled: !!selectedKB,
        knowledgeBaseId: selectedKB || undefined,
      });
    },
    [input, addMessage, startStream, currentSessionId, selectedKB],
  );

  return (
    <div className="flex h-full">
      {/* ── 左侧：会话列表 ── */}
      <div className="w-60 shrink-0 border-r border-gray-200 bg-gray-50 flex flex-col">
        <div className="p-3 border-b border-gray-200">
          <Button onClick={handleNewSession} className="w-full" size="sm">
            <Plus className="mr-1 size-4" /> 新建会话
          </Button>
        </div>
        <div className="flex-1 overflow-y-auto">
          {Array.isArray(sessions) && sessions.map((s) => (
            <button
              key={s.sessionId}
              onClick={() => selectSession(s.sessionId)}
              className={cn(
                'w-full px-4 py-3 text-left text-sm border-l-3 transition-colors',
                currentSessionId === s.sessionId
                  ? 'bg-indigo-50 border-l-indigo-500 font-medium text-gray-900'
                  : 'border-l-transparent text-gray-600 hover:bg-gray-100',
              )}
            >
              <div className="truncate">{s.name}</div>
              {s.lastMessage && (
                <div className="mt-0.5 truncate text-[11px] text-gray-400">{s.lastMessage}</div>
              )}
              <div className="mt-0.5 text-[11px] text-gray-400">
                {s.createdAt
                  ? new Date(s.createdAt).toLocaleString('zh-CN', {
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })
                  : ''}
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* ── 右侧：聊天区域 ── */}
      <div className="flex flex-1 flex-col">
        {/* 顶栏 */}
        <div className="flex h-14 shrink-0 items-center justify-between border-b border-gray-200 px-5">
          <div className="flex items-center gap-2">
            <Bot className="size-4 text-indigo-500" />
            <span className="text-sm font-semibold text-gray-900">{currentSessionName}</span>
            <Badge variant="outline" className="text-xs text-indigo-600 border-indigo-300">
              多轮对话
            </Badge>
          </div>
          {/* 知识库选择 */}
          <div className="flex items-center gap-2">
            <Database className="size-4 text-gray-400" />
            <select
              className="h-7 rounded-md border border-gray-200 px-2 text-xs text-gray-600 outline-none focus:border-indigo-300"
              value={selectedKB}
              onChange={(e) => setSelectedKB(e.target.value)}
            >
              <option value="">不使用知识库</option>
              {knowledgeBases.map((kb) => (
                <option key={kb.baseId} value={kb.baseId}>
                  {kb.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* 消息区域 */}
        <div ref={scrollRef} className="flex-1 overflow-y-auto px-12 py-6">
          {messages.length === 0 && (
            <div className="mt-20 text-center text-gray-400">开始新对话吧！</div>
          )}
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={cn(
                'mb-4 flex',
                msg.role === 'user' ? 'justify-end' : 'justify-start',
              )}
            >
              <div className="flex items-end gap-2 max-w-[65%]">
                {msg.role === 'assistant' && (
                  <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-indigo-100 text-indigo-600">
                    <Bot className="size-3.5" />
                  </div>
                )}
                <div
                  className={cn(
                    'rounded-2xl px-4 py-2.5 text-sm leading-relaxed',
                    msg.role === 'user'
                      ? 'bg-indigo-500 text-white rounded-br-sm'
                      : 'bg-gray-100 text-gray-800 rounded-bl-sm',
                  )}
                >
                  {msg.content || (isStreaming && msg.role === 'assistant' ? (
                    <span className="flex items-center gap-1.5">
                      <span className="inline-block size-1.5 animate-bounce rounded-full bg-indigo-400 [animation-delay:0ms]" />
                      <span className="inline-block size-1.5 animate-bounce rounded-full bg-indigo-400 [animation-delay:150ms]" />
                      <span className="inline-block size-1.5 animate-bounce rounded-full bg-indigo-400 [animation-delay:300ms]" />
                    </span>
                  ) : null)}
                </div>
                {msg.role === 'user' && (
                  <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-indigo-500 text-white">
                    <User className="size-3.5" />
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>

        {/* 输入区域 */}
        <div className="border-t border-gray-200 px-12 py-4">
          <div className="flex items-end gap-2">
            <textarea
              className="flex-1 resize-none rounded-xl border border-gray-200 px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-indigo-200"
              style={{ minHeight: 42, maxHeight: 120 }}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSend();
                }
              }}
              placeholder="输入消息... (Enter 发送，Shift+Enter 换行)"
            />
            <Button onClick={() => handleSend()} disabled={!input.trim() || isStreaming}>
              发送
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default memo(SimpleChatPage);
