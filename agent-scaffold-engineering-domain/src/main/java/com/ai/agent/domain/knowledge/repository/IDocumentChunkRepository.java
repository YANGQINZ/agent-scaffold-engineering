package com.ai.agent.domain.knowledge.repository;

import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;

import java.util.List;

/**
 * 文档分块仓储接口
 */
public interface IDocumentChunkRepository {

    /**
     * 批量保存文档分块
     */
    void saveBatch(List<DocumentChunk> chunks);

    /**
     * 向量相似度搜索
     */
    List<DocumentChunk> vectorSearch(String baseId, float[] queryEmbedding, int topK);

    /**
     * BM25关键词搜索
     */
    List<DocumentChunk> bm25Search(String baseId, String query, int topK);

}
