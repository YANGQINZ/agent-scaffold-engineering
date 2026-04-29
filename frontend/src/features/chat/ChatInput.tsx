/**
 * 聊天输入区域组件
 * 自动扩展文本框（最多4行后滚动）、发送按钮
 * Shift+Enter 换行、Enter 发送、流式输出时禁用
 */
import { memo, useState, useCallback, useRef, type KeyboardEvent } from 'react';
import { SendHorizontal } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useChatStore } from '@/stores/chat';
import { cn } from '@/lib/utils';

interface ChatInputProps {
  /** 发送消息回调 */
  onSend: (text: string) => void;
}

function ChatInput({ onSend }: ChatInputProps) {
  const [value, setValue] = useState('');
  const isStreaming = useChatStore((s) => s.isStreaming);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  /** 自动调整高度 */
  const adjustHeight = useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = 'auto';
    // 最多显示4行（约 24px line-height * 4）
    const maxH = 24 * 4;
    el.style.height = `${Math.min(el.scrollHeight, maxH)}px`;
    el.style.overflowY = el.scrollHeight > maxH ? 'auto' : 'hidden';
  }, []);

  /** 发送消息 */
  const handleSend = useCallback(() => {
    const trimmed = value.trim();
    if (!trimmed || isStreaming) return;
    onSend(trimmed);
    setValue('');
    // 重置高度
    requestAnimationFrame(() => {
      if (textareaRef.current) {
        textareaRef.current.style.height = 'auto';
        textareaRef.current.style.overflowY = 'hidden';
      }
    });
  }, [value, isStreaming, onSend]);

  /** 键盘事件：Enter 发送 / Shift+Enter 换行 */
  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    },
    [handleSend],
  );

  /** 输入变更 */
  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      setValue(e.target.value);
      adjustHeight();
    },
    [adjustHeight],
  );

  return (
    <div className="flex items-end gap-2 border-t border-gray-200 bg-white px-4 py-3">
      <textarea
        ref={textareaRef}
        value={value}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        disabled={isStreaming}
        placeholder="输入消息与 Agent 对话..."
        rows={1}
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
        disabled={!value.trim() || isStreaming}
        onClick={handleSend}
      >
        <SendHorizontal className="size-4" />
      </Button>
    </div>
  );
}

export default memo(ChatInput);
