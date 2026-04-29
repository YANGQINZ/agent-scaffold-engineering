/**
 * 思考过程折叠组件（专家模式）
 * 在助手消息内容上方展示可折叠的思考过程文本
 */
import { memo, useState } from 'react';
import { ChevronRight, Brain } from 'lucide-react';
import { cn } from '@/lib/utils';

interface ThinkingBlockProps {
  /** 思考内容文本 */
  content: string;
}

function ThinkingBlock({ content }: ThinkingBlockProps) {
  const [expanded, setExpanded] = useState(false);

  if (!content) return null;

  return (
    <div className="mb-2 overflow-hidden rounded-lg border border-gray-200 bg-gray-50">
      {/* 折叠头部 */}
      <button
        type="button"
        className="flex w-full items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-gray-600 hover:bg-gray-100 transition-colors"
        onClick={() => setExpanded((prev) => !prev)}
      >
        <Brain className="size-3.5 text-purple-500" />
        <span>思考过程</span>
        <ChevronRight
          className={cn(
            'size-3 text-gray-400 transition-transform duration-200',
            expanded && 'rotate-90',
          )}
        />
      </button>

      {/* 折叠内容 */}
      {expanded && (
        <div className="border-t border-gray-200 px-3 py-2">
          <pre className="whitespace-pre-wrap break-words font-mono text-xs leading-relaxed text-gray-600">
            {content}
          </pre>
        </div>
      )}
    </div>
  );
}

export default memo(ThinkingBlock);
