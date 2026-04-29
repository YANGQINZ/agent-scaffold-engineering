package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.DocumentChunkPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文档分块DAO
 */
@Mapper
public interface IDocumentChunkDao {

    /**
     * 批量插入文档分块
     */
    void insertBatch(List<DocumentChunkPO> list);

    /**
     * 向量相似度搜索
     */
    List<DocumentChunkPO> vectorSearch(String baseId, String embedding, int topK);

    /**
     * BM25关键词搜索
     */
    List<DocumentChunkPO> bm25Search(String baseId, String query, int topK);

    /**
     * 根据知识库ID查询文档分块列表
     */
    List<DocumentChunkPO> selectByBaseId(String baseId);

    /**
     * 根据知识库ID删除文档分块
     */
    int deleteByBaseId(String baseId);

}
