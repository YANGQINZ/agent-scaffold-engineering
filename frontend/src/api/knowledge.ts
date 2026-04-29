/**
 * 知识库相关 API
 * 对应后端 KnowledgeController
 */
import { apiClient, type ApiResponse } from './client';

// ═══════════════════════════════════════════════════════════
// 类型定义
// ═══════════════════════════════════════════════════════════

/** 知识库信息 */
export interface KnowledgeBase {
  /** 知识库ID */
  baseId: string;
  /** 知识库名称 */
  name: string;
  /** 描述 */
  description: string;
  /** 文档数量 */
  docCount: number;
}

/** 文档分块信息 */
export interface DocumentChunk {
  /** 分块ID */
  id: string;
  /** 分块内容 */
  content: string;
  /** 元数据 */
  metadata: string;
}

// ═══════════════════════════════════════════════════════════
// API 函数
// ═══════════════════════════════════════════════════════════

/**
 * 查询所有知识库列表
 * GET /knowledge/bases
 */
export async function listKnowledgeBases(): Promise<KnowledgeBase[]> {
  const res = await apiClient.get<ApiResponse<KnowledgeBase[]>>(
    '/knowledge/bases',
  );
  return res.data.data;
}

/**
 * 创建知识库（通过上传文件）
 * POST /knowledge/upload — multipart/form-data
 */
export async function uploadDocument(formData: FormData): Promise<void> {
  await apiClient.post('/knowledge/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/**
 * 删除知识库
 * DELETE /knowledge/bases/{id}
 */
export async function deleteKnowledgeBase(id: string): Promise<void> {
  await apiClient.delete(`/knowledge/bases/${id}`);
}

/**
 * 查询知识库下的文档分块列表
 * GET /knowledge/bases/{id}/documents
 */
export async function listDocuments(
  baseId: string,
): Promise<DocumentChunk[]> {
  const res = await apiClient.get<ApiResponse<DocumentChunk[]>>(
    `/knowledge/bases/${baseId}/documents`,
  );
  return res.data.data;
}
