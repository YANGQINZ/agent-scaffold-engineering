/**
 * 知识库列表组件
 * 三列网格布局展示所有知识库卡片
 * 空状态提示用户创建
 */
import { memo } from 'react';
import { BookOpen } from 'lucide-react';
import KnowledgeBaseCard from './KnowledgeBaseCard';
import type { KnowledgeBase } from '@/api/knowledge';

interface KnowledgeBaseListProps {
  /** 知识库列表数据 */
  bases: KnowledgeBase[];
  /** 当前选中的知识库 ID */
  selectedId: string | null;
  /** 选中回调 */
  onSelect: (baseId: string) => void;
  /** 上传文档回调 */
  onUpload: (baseId: string) => void;
  /** 删除回调 */
  onDelete: (baseId: string) => void;
}

function KnowledgeBaseList({
  bases,
  selectedId,
  onSelect,
  onUpload,
  onDelete,
}: KnowledgeBaseListProps) {
  if (bases.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-gray-400">
        <BookOpen className="mb-3 size-12 text-gray-300" />
        <p className="text-sm">暂无知识库，点击右上角创建</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
      {bases.map((base) => (
        <KnowledgeBaseCard
          key={base.baseId}
          base={base}
          selected={base.baseId === selectedId}
          onSelect={onSelect}
          onUpload={onUpload}
          onDelete={onDelete}
        />
      ))}
    </div>
  );
}

export default memo(KnowledgeBaseList);
