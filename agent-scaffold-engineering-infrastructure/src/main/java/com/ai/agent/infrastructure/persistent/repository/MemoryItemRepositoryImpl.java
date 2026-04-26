package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.memory.model.entity.MemoryItem;
import com.ai.agent.domain.memory.repository.IMemoryItemRepository;
import com.ai.agent.infrastructure.persistent.dao.IMemoryItemDao;
import com.ai.agent.infrastructure.persistent.po.MemoryItemPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 记忆记录仓储实现（冷层）
 */
@Repository
@RequiredArgsConstructor
public class MemoryItemRepositoryImpl implements IMemoryItemRepository {

    private final IMemoryItemDao memoryItemDao;

    @Override
    public void save(MemoryItem item) {
        memoryItemDao.insert(toPO(item));
    }

    @Override
    public void saveBatch(List<MemoryItem> items) {
        List<MemoryItemPO> poList = items.stream().map(this::toPO).toList();
        memoryItemDao.insertBatch(poList);
    }

    @Override
    public List<MemoryItem> searchBySimilarity(String sessionId, float[] queryEmbedding, int topK) {
        String embeddingStr = embeddingToString(queryEmbedding);
        List<MemoryItemPO> poList = memoryItemDao.vectorSearch(sessionId, embeddingStr, topK);
        return poList.stream().map(this::toEntity).toList();
    }

    @Override
    public MemoryItem findLatestSummary(String sessionId) {
        MemoryItemPO po = memoryItemDao.findLatestSummary(sessionId);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public List<MemoryItem> findBySessionId(String sessionId) {
        List<MemoryItemPO> poList = memoryItemDao.findBySessionId(sessionId);
        return poList.stream().map(this::toEntity).toList();
    }

    /**
     * MemoryItem 领域对象 -> MemoryItemPO
     */
    private MemoryItemPO toPO(MemoryItem item) {
        MemoryItemPO po = new MemoryItemPO();
        po.setId(item.getId());
        po.setSessionId(item.getSessionId());
        po.setContent(item.getContent());
        if (item.getEmbedding() != null) {
            po.setEmbedding(embeddingToString(item.getEmbedding()));
        }
        po.setImportance(item.getImportance());
        if (item.getTags() != null) {
            po.setTags(item.getTags().toArray(new String[0]));
        }
        if (item.getSourceMsgIds() != null) {
            po.setSourceMsgIds(item.getSourceMsgIds().toArray(new Long[0]));
        }
        po.setCreatedAt(item.getTimestamp());
        po.setMetadata(item.getMetadata());
        return po;
    }

    /**
     * MemoryItemPO -> MemoryItem 领域对象
     */
    private MemoryItem toEntity(MemoryItemPO po) {
        return MemoryItem.builder()
                .id(po.getId())
                .sessionId(po.getSessionId())
                .content(po.getContent())
                .importance(po.getImportance())
                .tags(po.getTags() != null ? Arrays.asList(po.getTags()) : List.of())
                .sourceMsgIds(po.getSourceMsgIds() != null ? Arrays.asList(po.getSourceMsgIds()) : List.of())
                .timestamp(po.getCreatedAt())
                .metadata(po.getMetadata())
                .score(po.getScore())
                .build();
    }

    /**
     * float[] 向量嵌入 -> PGvector 字符串格式
     */
    private String embeddingToString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
