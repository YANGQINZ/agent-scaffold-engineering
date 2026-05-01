package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.DocumentChunkPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档分块DAO
 */
@Mapper
public interface IDocumentChunkDao {

    /**
     * 批量插入文档分块
     */
    void insertBatch(@Param("list") List<DocumentChunkPO> list);

    /**
     * 向量相似度搜索
     */
    List<DocumentChunkPO> vectorSearch(@Param("baseId") String baseId,
                                       @Param("embedding") String embedding,
                                       @Param("topK") int topK);

    /**
     * BM25关键词搜索
     */
    List<DocumentChunkPO> bm25Search(@Param("baseId") String baseId,
                                      @Param("query") String query,
                                      @Param("topK") int topK);

    /**
     * 根据知识库ID查询文档分块列表
     */
    List<DocumentChunkPO> selectByBaseId(@Param("baseId") String baseId);

    /**
     * 根据知识库ID删除文档分块
     */
    int deleteByBaseId(@Param("baseId") String baseId);

}
