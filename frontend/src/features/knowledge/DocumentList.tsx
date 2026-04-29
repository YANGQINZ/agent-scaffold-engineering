/**
 * 文档列表组件
 * 展示知识库下的所有文档分块，支持上传新文档
 */
import { memo, useCallback, useRef, useState } from 'react';
import { FileText, Upload, File } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { uploadDocument, listDocuments } from '@/api/knowledge';
import type { DocumentChunk } from '@/api/knowledge';

interface DocumentListProps {
  /** 知识库 ID */
  baseId: string;
  /** 初始文档列表 */
  documents: DocumentChunk[];
  /** 上传成功后刷新回调 */
  onRefresh: () => void;
}

function DocumentList({ baseId, documents, onRefresh }: DocumentListProps) {
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  /** 选择文件并上传 */
  const handleFileChange = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (!file) return;
      setUploading(true);
      try {
        await uploadDocument(baseId, file);
        onRefresh();
      } catch (err) {
        console.error('上传文档失败:', err);
      } finally {
        setUploading(false);
        // 重置 file input 以便重复选择同一文件
        e.target.value = '';
      }
    },
    [baseId, onRefresh],
  );

  /** 打开文件选择器 */
  const handleOpenFilePicker = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  return (
    <div className="space-y-3">
      {/* 标题栏 */}
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-gray-700">文档列表</h3>
        <div className="flex items-center gap-2">
          <Badge variant="secondary">{documents.length} 个分块</Badge>
          <input
            ref={fileInputRef}
            type="file"
            className="hidden"
            accept=".pdf,.docx,.doc,.txt,.md"
            onChange={handleFileChange}
          />
          <Button
            variant="outline"
            size="sm"
            onClick={handleOpenFilePicker}
            disabled={uploading}
          >
            <Upload className="size-3.5" />
            {uploading ? '上传中...' : '上传文档'}
          </Button>
        </div>
      </div>

      {/* 文档列表 */}
      {documents.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-gray-300 py-10 text-gray-400">
          <FileText className="mb-2 size-8 text-gray-300" />
          <p className="text-sm">暂无文档，点击上方按钮上传</p>
        </div>
      ) : (
        <div className="divide-y divide-gray-100 rounded-lg border border-gray-200 bg-white">
          {documents.map((doc) => (
            <div
              key={doc.id}
              className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-gray-50"
            >
              <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-indigo-50 text-indigo-500">
                <File className="size-4" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-gray-800">
                  {doc.metadata || doc.id}
                </p>
                <p className="truncate text-xs text-gray-400">
                  {doc.content.slice(0, 100)}
                  {doc.content.length > 100 ? '...' : ''}
                </p>
              </div>
              <Badge variant="outline" className="shrink-0 text-xs">
                ID: {doc.id.slice(0, 8)}
              </Badge>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default memo(DocumentList);
