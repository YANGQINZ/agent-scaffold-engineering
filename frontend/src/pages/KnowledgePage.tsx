/**
 * 知识库管理页面
 * 布局：顶部标题 + 创建按钮 → 知识库卡片网格 → 选中知识库的文档列表
 */
import { useCallback, useEffect, useState } from 'react';
import {
  listKnowledgeBases,
  deleteKnowledgeBase,
  listDocuments,
} from '@/api/knowledge';
import type { KnowledgeBase, DocumentChunk } from '@/api/knowledge';
import KnowledgeBaseList from '@/features/knowledge/KnowledgeBaseList';
import CreateBaseDialog from '@/features/knowledge/CreateBaseDialog';
import DocumentList from '@/features/knowledge/DocumentList';
import { Separator } from '@/components/ui/separator';

function KnowledgePage() {
  const [bases, setBases] = useState<KnowledgeBase[]>([]);
  const [selectedBaseId, setSelectedBaseId] = useState<string | null>(null);
  const [documents, setDocuments] = useState<DocumentChunk[]>([]);
  const [loadingDocs, setLoadingDocs] = useState(false);

  /** 加载知识库列表 */
  const fetchBases = useCallback(async () => {
    try {
      const data = await listKnowledgeBases();
      setBases(data);
    } catch (err) {
      console.error('加载知识库列表失败:', err);
    }
  }, []);

  /** 加载文档列表 */
  const fetchDocuments = useCallback(async (baseId: string) => {
    setLoadingDocs(true);
    try {
      const data = await listDocuments(baseId);
      setDocuments(data);
    } catch (err) {
      console.error('加载文档列表失败:', err);
      setDocuments([]);
    } finally {
      setLoadingDocs(false);
    }
  }, []);

  /** 初始加载 */
  useEffect(() => {
    fetchBases();
  }, [fetchBases]);

  /** 选中知识库变化时加载文档 */
  useEffect(() => {
    if (selectedBaseId) {
      fetchDocuments(selectedBaseId);
    } else {
      setDocuments([]);
    }
  }, [selectedBaseId, fetchDocuments]);

  /** 选中知识库 */
  const handleSelect = useCallback((baseId: string) => {
    setSelectedBaseId((prev) => (prev === baseId ? null : baseId));
  }, []);

  /** 上传文档（打开文件选择器由 DocumentList 内部处理） */
  const handleUpload = useCallback(
    (baseId: string) => {
      // 先选中该知识库，DocumentList 组件中有上传按钮
      setSelectedBaseId(baseId);
    },
    [],
  );

  /** 删除知识库 */
  const handleDelete = useCallback(
    async (baseId: string) => {
      try {
        await deleteKnowledgeBase(baseId);
        if (selectedBaseId === baseId) {
          setSelectedBaseId(null);
          setDocuments([]);
        }
        fetchBases();
      } catch (err) {
        console.error('删除知识库失败:', err);
      }
    },
    [selectedBaseId, fetchBases],
  );

  /** 创建成功后刷新 */
  const handleCreated = useCallback(() => {
    fetchBases();
  }, [fetchBases]);

  /** 文档列表刷新 */
  const handleDocsRefresh = useCallback(() => {
    if (selectedBaseId) {
      fetchDocuments(selectedBaseId);
      fetchBases(); // 文档数量可能变化
    }
  }, [selectedBaseId, fetchDocuments, fetchBases]);

  /** 当前选中的知识库名称 */
  const selectedBase = bases.find((b) => b.baseId === selectedBaseId);

  return (
    <div className="h-full overflow-y-auto p-6">
      {/* ── 顶部：标题 + 创建按钮 ── */}
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">知识库管理</h1>
        <CreateBaseDialog onCreated={handleCreated} />
      </div>

      {/* ── 知识库卡片网格 ── */}
      <KnowledgeBaseList
        bases={bases}
        selectedId={selectedBaseId}
        onSelect={handleSelect}
        onUpload={handleUpload}
        onDelete={handleDelete}
      />

      {/* ── 文档列表区域 ── */}
      {selectedBaseId && selectedBase && (
        <>
          <Separator className="my-6" />
          <div className="mb-3 flex items-center gap-2">
            <h2 className="text-sm font-semibold text-gray-700">
              文档列表 — {selectedBase.name}
            </h2>
          </div>
          {loadingDocs ? (
            <div className="flex items-center justify-center py-8 text-sm text-gray-400">
              加载中...
            </div>
          ) : (
            <DocumentList
              baseId={selectedBaseId}
              documents={documents}
              onRefresh={handleDocsRefresh}
            />
          )}
        </>
      )}
    </div>
  );
}

export default KnowledgePage;
