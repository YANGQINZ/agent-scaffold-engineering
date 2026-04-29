/**
 * 知识库卡片组件
 * 展示单个知识库信息：名称、描述、文档数量、操作按钮
 * 点击卡片选中并展开文档列表
 */
import { memo, useCallback } from 'react';
import { BookOpen, Upload, Trash2 } from 'lucide-react';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import type { KnowledgeBase } from '@/api/knowledge';

interface KnowledgeBaseCardProps {
  /** 知识库数据 */
  base: KnowledgeBase;
  /** 是否选中 */
  selected: boolean;
  /** 点击选中回调 */
  onSelect: (baseId: string) => void;
  /** 上传文档回调 */
  onUpload: (baseId: string) => void;
  /** 删除回调 */
  onDelete: (baseId: string) => void;
}

function KnowledgeBaseCard({
  base,
  selected,
  onSelect,
  onUpload,
  onDelete,
}: KnowledgeBaseCardProps) {
  /** 选中卡片 */
  const handleSelect = useCallback(() => {
    onSelect(base.baseId);
  }, [onSelect, base.baseId]);

  /** 上传文档 */
  const handleUpload = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onUpload(base.baseId);
    },
    [onUpload, base.baseId],
  );

  /** 删除知识库 */
  const handleDelete = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onDelete(base.baseId);
    },
    [onDelete, base.baseId],
  );

  return (
    <Card
      className={cn(
        'cursor-pointer transition-all hover:shadow-md',
        selected && 'ring-2 ring-indigo-500 shadow-md',
      )}
      onClick={handleSelect}
    >
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <div className="flex size-8 items-center justify-center rounded-lg bg-indigo-100 text-indigo-600">
            <BookOpen className="size-4" />
          </div>
          <span className="truncate">{base.name}</span>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <p className="text-sm text-gray-500 line-clamp-2">
          {base.description || '暂无描述'}
        </p>
        <Badge variant="secondary">{base.docCount} 文档</Badge>
      </CardContent>
      <CardFooter className="gap-2">
        <Button variant="outline" size="sm" onClick={handleUpload}>
          <Upload className="size-3.5" />
          上传
        </Button>
        <Button variant="destructive" size="sm" onClick={handleDelete}>
          <Trash2 className="size-3.5" />
          删除
        </Button>
      </CardFooter>
    </Card>
  );
}

export default memo(KnowledgeBaseCard);
