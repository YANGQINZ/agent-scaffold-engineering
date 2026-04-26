package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.MemoryItemPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 记忆记录DAO
 */
@Mapper
public interface IMemoryItemDao {

    /**
     * 插入单条记忆
     */
    void insert(MemoryItemPO po);

    /**
     * 批量插入记忆
     */
    void insertBatch(@Param("list") List<MemoryItemPO> poList);

    /**
     * 按语义相似度检索（仅当前会话）
     */
    List<MemoryItemPO> vectorSearch(@Param("sessionId") String sessionId,
                                     @Param("embedding") String embedding,
                                     @Param("topK") int topK);

    /**
     * 查找最新摘要（tags 包含 'summary'）
     */
    MemoryItemPO findLatestSummary(@Param("sessionId") String sessionId);

    /**
     * 按会话ID查询所有记忆
     */
    List<MemoryItemPO> findBySessionId(@Param("sessionId") String sessionId);
}
