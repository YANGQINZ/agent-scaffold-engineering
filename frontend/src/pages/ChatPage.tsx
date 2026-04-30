/**
 * 多轮对话页面
 * 左侧 240px 会话列表 + 右侧聊天区域（消息列表 + 输入框）
 * 使用 useChatSessionStore 管理会话、useChatStore 管理消息
 */
import { memo, useCallback, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bot, User, Plus, ArrowUpRight } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { useChatStore } from '@/stores/chat';
import { useChatSessionStore } from '@/stores/chatSession';
import { useSSE } from '@/hooks/useSSE';
import { cn } from '@/lib/utils';

/** 生成唯一消息 ID */
const genMsgId = () =>
  `msg_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

function ChatPage() {
  const navigate = useNavigate();

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
  const clearMessages = useChatStore((s) => s.clearMessages);
  const setActiveSessionId = useChatStore((s) => s.setActiveSessionId);

  // 流式对话
  const { startStream } = useSSE();

  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // 当前会话名称
  const currentSession = sessions.find((s) => s.sessionId === currentSessionId);
  const currentSessionName = currentSession?.name ?? '多轮对话';

  /** 挂载时加载会话列表 */
  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  /** 消息更新时自动滚动到底部 */
  useEffect(() => {
    const el = scrollRef.current;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages]);

  /** 新建会话 */
  const handleCreateSession = useCallback(async () => {
    clearMessages();
    await createNewSession();
  }, [clearMessages, createNewSession]);

  /** 选择会话 */
  const handleSelectSession = useCallback(
    (sessionId: string) => {
      clearMessages();
      selectSession(sessionId);
      setActiveSessionId(sessionId);
    },
    [clearMessages, selectSession, setActiveSessionId],
  );

  /** 发送消息 */
  const handleSend = useCallback(
    (text: string) => {
      if (!text.trim() || isStreaming) return;

      // 添加用户消息
      addMessage({
        id: genMsgId(),
        role: 'user',
        content: text,
        timestamp: Date.now(),
      });

      // 添加空的助手消息占位
      addMessage({
        id: genMsgId(),
        role: 'assistant',
        content: '',
        timestamp: Date.now(),
      });

      // 发起流式对话
      startStream({
        query: text,
        userId: 'web-user',
        sessionId: currentSessionId ?? undefined,
        mode: 'MULTI_TURN',
      });
    },
    [addMessage, startStream, currentSessionId, isStreaming],
  );

  /** 输入框键盘事件 */
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        const value = (e.target as HTMLTextAreaElement).value;
        handleSend(value);
        // 清空输入
        (e.target as HTMLTextAreaElement).value = '';
        // 重置高度
        requestAnimationFrame(() => {
          if (inputRef.current) {
            inputRef.current.style.height = 'auto';
            inputRef.current.style.overflowY = 'hidden';
          }
        });
      }
    },
    [handleSend],
  );

  /** 自动调整输入框高度 */
  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      const el = e.target;
      el.style.height = 'auto';
      const maxH = 24 * 4;
      el.style.height = `${Math.min(el.scrollHeight, maxH)}px`;
      el.style.overflowY = el.scrollHeight > maxH ? 'auto' : 'hidden';
    },
    [],
  );

  /** 点击发送按钮 */
  const handleSendClick = useCallback(() => {
    const value = inputRef.current?.value?.trim();
    if (value) {
      handleSend(value);
      if (inputRef.current) {
        inputRef.current.value = '';
        inputRef.current.style.height = 'auto';
        inputRef.current.style.overflowY = 'hidden';
      }
    }
  }, [handleSend]);

  return (
    <div className="flex h-full">
      {/* ── 左侧：会话列表（240px） ── */}
      <div className="flex w-[240px] shrink-0 flex-col border-r border-gray-200 bg-gray-50">
        {/* 新建会话按钮 */}
        <div className="p-3">
          <Button
            variant="outline"
            className="w-full justify-start gap-2 text-sm"
            onClick={handleCreateSession}
          >
            <Plus className="size-4" />
            新建会话
          </Button>
        </div>

        {/* 会话列表 */}
        <div className="flex-1 overflow-y-auto px-2">
          {sessions.map((session) => (
            <button
              key={session.sessionId}
              type="button"
              className={cn(
                'w-full rounded-md px-3 py-2.5 text-left text-sm transition-colors',
                session.sessionId === currentSessionId
                  ? 'bg-indigo-50 text-indigo-700 font-medium'
                  : 'text-gray-700 hover:bg-gray-100',
              )}
              onClick={() => handleSelectSession(session.sessionId)}
            >
              <div className="truncate">{session.name}</div>
            </button>
          ))}
          {sessions.length === 0 && (
            <div className="px-3 py-6 text-center text-xs text-gray-400">
              暂无会话
            </div>
          )}
        </div>
      </div>

      {/* ── 右侧：聊天区域 ── */}
      <div className="flex flex-1 flex-col bg-white">
        {/* 顶部：会话标题 + 操作按钮 */}
        <div className="flex items-center gap-2 border-b border-gray-200 px-4 py-3">
          <div className="flex size-8 items-center justify-center rounded-full bg-indigo-100 text-indigo-600">
            <Bot className="size-4" />
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-sm font-semibold text-gray-900">
              {currentSessionName}
            </h2>
          </div>
          <Badge variant="outline" className="text-xs text-indigo-600 border-indigo-300">
            多轮对话
          </Badge>
          <Button
            variant="ghost"
            size="sm"
            className="gap-1.5 text-xs text-gray-500 hover:text-indigo-600"
            onClick={() => navigate('/')}
          >
            切换专家模式
            <ArrowUpRight className="size-3.5" />
          </Button>
        </div>

        {/* 中间：消息列表 */}
        <div ref={scrollRef} className="flex-1 overflow-y-auto">
          {messages.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center gap-3 text-gray-400">
              <Bot className="size-12 text-gray-300" />
              <p className="text-sm">开始多轮对话</p>
            </div>
          ) : (
            <div className="py-4 space-y-1">
              {messages.map((msg) => {
                const isUser = msg.role === 'user';
                return (
                  <div
                    key={msg.id}
                    className={cn(
                      'flex gap-2 px-4 py-2',
                      isUser ? 'justify-end' : 'justify-start',
                    )}
                  >
                    {/* 助手头像 */}
                    {!isUser && (
                      <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-indigo-100 text-indigo-600">
                        <Bot className="size-4" />
                      </div>
                    )}

                    {/* 消息内容 */}
                    <div
                      className={cn(
                        'max-w-[75%] min-w-0 rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed',
                        isUser
                          ? 'bg-indigo-500 text-white'
                          : 'border border-gray-200 bg-white text-gray-800',
                      )}
                    >
                      <div className="whitespace-pre-wrap break-words">
                        {msg.content}
                      </div>
                    </div>

                    {/* 用户头像 */}
                    {isUser && (
                      <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-indigo-500 text-white">
                        <User className="size-4" />
                      </div>
                    )}
                  </div>
                );
              })}
              {/* 流式输出指示器 */}
              {isStreaming && (
                <div className="flex justify-start px-4 py-2">
                  <div className="flex items-center gap-1.5">
                    <span className="inline-block size-1.5 animate-bounce rounded-full bg-indigo-400 [animation-delay:0ms]" />
                    <span className="inline-block size-1.5 animate-bounce rounded-full bg-indigo-400 [animation-delay:150ms]" />
                    <span className="inline-block size-1.5 animate-bounce rounded-full bg-indigo-400 [animation-delay:300ms]" />
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* 底部：输入区 */}
        <div className="flex items-end gap-2 border-t border-gray-200 bg-white px-4 py-3">
          <textarea
            ref={inputRef}
            placeholder="输入消息..."
            rows={1}
            disabled={isStreaming}
            onKeyDown={handleKeyDown}
            onChange={handleInputChange}
            className={cn(
              'flex-1 resize-none rounded-lg border border-gray-200 bg-transparent px-3 py-2 text-sm outline-none transition-colors',
              'placeholder:text-gray-400',
              'focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100',
              'disabled:cursor-not-allowed disabled:opacity-50',
              'max-h-[96px] overflow-y-hidden',
            )}
          />
          <Button
            size="icon"
            className={cn(
              'shrink-0 rounded-lg bg-indigo-500 text-white hover:bg-indigo-600',
              'disabled:opacity-50 disabled:cursor-not-allowed',
            )}
            disabled={isStreaming}
            onClick={handleSendClick}
          >
            <ArrowUpRight className="size-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}

export default memo(ChatPage);
