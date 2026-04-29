/**
 * 聊天面板主组件
 * 布局：顶部（Agent 名称 + 模式标签）→ 中间（可滚动消息列表）→ 底部（输入区）
 * 使用 useSSE hook 管理流式对话，自动滚动到最新消息
 */
import { memo, useCallback, useRef, useEffect } from 'react';
import { Bot } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { useChatStore } from '@/stores/chat';
import { useCanvasStore } from '@/stores/canvas';
import { useAppStore } from '@/stores/app';
import { useSSE } from '@/hooks/useSSE';
import { cn } from '@/lib/utils';
import MessageBubble from './MessageBubble';
import ChatInput from './ChatInput';
import NodeExecutionStatus from './NodeExecutionStatus';

/** 生成唯一消息 ID */
const genMsgId = () =>
  `msg_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

function ChatPanel() {
  const messages = useChatStore((s) => s.messages);
  const isStreaming = useChatStore((s) => s.isStreaming);
  const addMessage = useChatStore((s) => s.addMessage);
  const setIsStreaming = useChatStore((s) => s.setIsStreaming);
  const activeSessionId = useChatStore((s) => s.activeSessionId);
  const selectedAgentId = useChatStore((s) => s.selectedAgentId);
  const agents = useCanvasStore((s) => s.agents);
  const mode = useAppStore((s) => s.mode);

  const { startStream } = useSSE();
  const scrollRef = useRef<HTMLDivElement>(null);

  /** 当前选中的 Agent 定义 */
  const activeAgent = agents.find((a) => a.agentId === selectedAgentId);

  /** 自动滚动到底部 */
  useEffect(() => {
    const el = scrollRef.current;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages]);

  /** 发送消息 */
  const handleSend = useCallback(
    (text: string) => {
      // 1. 添加用户消息
      addMessage({
        id: genMsgId(),
        role: 'user',
        content: text,
        timestamp: Date.now(),
      });

      // 2. 添加空的助手消息占位（用于流式追加）
      addMessage({
        id: genMsgId(),
        role: 'assistant',
        content: '',
        timestamp: Date.now(),
      });

      // 3. 发起流式请求
      startStream({
        query: text,
        sessionId: activeSessionId ?? undefined,
        agentId: selectedAgentId ?? undefined,
        mode: 'MULTI_TURN',
      });
    },
    [addMessage, startStream, activeSessionId, selectedAgentId],
  );

  return (
    <div className="flex h-full flex-col bg-white">
      {/* ── 顶部：Agent 信息 ── */}
      <div className="flex items-center gap-2 border-b border-gray-200 px-4 py-3">
        <div className="flex size-8 items-center justify-center rounded-full bg-indigo-100 text-indigo-600">
          <Bot className="size-4" />
        </div>
        <div className="min-w-0 flex-1">
          <h2 className="truncate text-sm font-semibold text-gray-900">
            {activeAgent?.name ?? 'Agent 对话'}
          </h2>
          {activeAgent && (
            <span className="text-xs text-gray-400">{activeAgent.agentId}</span>
          )}
        </div>
        {mode === 'expert' && (
          <Badge variant="outline" className="text-xs text-indigo-600 border-indigo-300">
            专家模式
          </Badge>
        )}
      </div>

      {/* ── 节点执行状态（专家模式） ── */}
      <NodeExecutionStatus />

      {/* ── 中间：消息列表 ── */}
      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto"
      >
        {messages.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center gap-3 text-gray-400">
            <Bot className="size-12 text-gray-300" />
            <p className="text-sm">开始与 Agent 对话</p>
          </div>
        ) : (
          <div className="py-4 space-y-1">
            {messages.map((msg) => (
              <MessageBubble key={msg.id} message={msg} />
            ))}
            {/* 流式输出时底部指示器 */}
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

      {/* ── 底部：输入区 ── */}
      <ChatInput onSend={handleSend} />
    </div>
  );
}

export default memo(ChatPanel);
