/**
 * RAG 知识库引用展示组件
 * 在助手消息内容下方展示引用的知识库文档来源
 */
import { memo } from 'react';
import { Paperclip } from 'lucide-react';
import type { Source } from '@/api/chat';

interface RagSourceBadgeProps {
  /** 引用来源列表 */
  sources: Source[];
}

function RagSourceBadge({ sources }: RagSourceBadgeProps) {
  if (!sources || sources.length === 0) return null;

  return (
    <div className="mt-2 overflow-hidden rounded-lg border border-yellow-300 bg-yellow-50">
      {/* 头部 */}
      <div className="flex items-center gap-1.5 border-b border-yellow-200 px-3 py-1.5">
        <Paperclip className="size-3.5 text-yellow-600" />
        <span className="text-xs font-medium text-yellow-700">
          知识库引用 ({sources.length})
        </span>
      </div>

      {/* 引用列表 */}
      <div className="px-3 py-2 space-y-1.5">
        {sources.map((source, idx) => (
          <div
            key={idx}
            className="flex items-start gap-2 text-xs text-yellow-800"
          >
            <span className="shrink-0 font-mono text-yellow-500">
              [{idx + 1}]
            </span>
            <div className="min-w-0 flex-1">
              <span className="font-medium">{source.docName}</span>
              {source.score !== undefined && (
                <span className="ml-2 text-yellow-600">
                  相关度: {(source.score * 100).toFixed(1)}%
                </span>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default memo(RagSourceBadge);
