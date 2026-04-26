package com.ai.agent.domain.memory.repository;

import com.ai.agent.domain.memory.model.entity.MemoryItem;

import java.util.List;

/**
 * memory_item 表操作接口（冷层）
 */
public interface IMemoryItemRepository {

    /**
     * 保存单条记忆
     */
    void save(MemoryItem item);

    /**
     * 批量保存记忆
     */
    void saveBatch(List<MemoryItem> items);

    /**
     * 按语义相似度检索记忆（仅当前会话）
     */
    List<MemoryItem> searchBySimilarity(String sessionId, float[] queryEmbedding, int topK);

    /**
     * 查找最新摘要记录（tags=['summary']）
     */
    MemoryItem findLatestSummary(String sessionId);

    /**
     * 按会话ID查询所有记忆
     */
    List<MemoryItem> findBySessionId(String sessionId);
}
