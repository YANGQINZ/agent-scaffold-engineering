/**
 * 聊天消息气泡组件
 * 用户消息：右对齐、靛蓝色背景、白色文字
 * 助手消息：左对齐、白色背景、灰色边框
 * 展示思考过程和 RAG 来源
 */
import { memo } from 'react';
import { Bot, User, Sparkles } from 'lucide-react';
import type { Message } from '@/stores/chat';
import { useChatStore } from '@/stores/chat';
import ThinkingBlock from './ThinkingBlock';
import RagSourceBadge from './RagSourceBadge';
import { cn } from '@/lib/utils';

interface MessageBubbleProps {
  /** 消息对象 */
  message: Message;
}

function MessageBubble({ message }: MessageBubbleProps) {
  const isUser = message.role === 'user';
  const isStreaming = useChatStore((s) => s.isStreaming);

  // 助手消息内容为空 + 正在流式输出 → 显示加载提示
  const isLoading = !isUser && !message.content && isStreaming;

  return (
    <div
      className={cn(
        'flex gap-2 px-4 py-2',
        isUser ? 'justify-end' : 'justify-start',
      )}
    >
      {/* 助手头像（左侧） */}
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
        {/* 思考过程（仅助手消息） */}
        {!isUser && message.thinkingContent && (
          <ThinkingBlock content={message.thinkingContent} />
        )}

        {/* 消息正文 */}
        {isLoading ? (
          <div className="flex items-center gap-2 text-gray-400">
            <Sparkles className="size-4 animate-pulse text-indigo-400" />
            <span className="text-sm">AI 正在思考，请稍等~</span>
          </div>
        ) : (
          <div className="whitespace-pre-wrap break-words">{message.content}</div>
        )}

        {/* RAG 来源（仅助手消息） */}
        {!isUser && message.sources && message.sources.length > 0 && (
          <RagSourceBadge sources={message.sources} />
        )}
      </div>

      {/* 用户头像（右侧） */}
      {isUser && (
        <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-indigo-500 text-white">
          <User className="size-4" />
        </div>
      )}
    </div>
  );
}

export default memo(MessageBubble);
