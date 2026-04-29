package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;
import com.ai.agent.domain.knowledge.repository.IKnowledgeBaseRepository;
import com.ai.agent.infrastructure.persistent.dao.IKnowledgeBaseDao;
import com.ai.agent.infrastructure.persistent.po.KnowledgeBasePO;
import com.ai.agent.types.enums.OwnerType;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 知识库仓储实现
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeBaseRepositoryImpl implements IKnowledgeBaseRepository {

    private final IKnowledgeBaseDao knowledgeBaseDao;

    @Override
    public long save(KnowledgeBase knowledgeBase) {
        long baseId = IdWorker.getId();
        KnowledgeBasePO po = new KnowledgeBasePO();
        po.setBaseId(baseId);
        po.setName(knowledgeBase.getName());
        po.setFileName(knowledgeBase.getFileName());
        po.setFileType(knowledgeBase.getFileType());
        po.setDocCount(knowledgeBase.getDocCount());
        po.setCreatedAt(knowledgeBase.getCreatedAt());
        knowledgeBaseDao.insert(po);
        return baseId;
    }

    @Override
    public KnowledgeBase findById(String baseId) {
        KnowledgeBasePO po = knowledgeBaseDao.selectById(baseId);
        if (po == null) {
            return null;
        }
        return toKnowledgeBase(po);
    }

    @Override
    public List<KnowledgeBase> findAll() {
        List<KnowledgeBasePO> poList = knowledgeBaseDao.selectAll();
        return poList.stream().map(this::toKnowledgeBase).toList();
    }

    @Override
    public void deleteById(String baseId) {
        knowledgeBaseDao.deleteById(baseId);
    }

    /**
     * KnowledgeBasePO -> KnowledgeBase 领域对象
     */
    private KnowledgeBase toKnowledgeBase(KnowledgeBasePO po) {
        return KnowledgeBase.builder()
                .baseId(po.getBaseId())
                .name(po.getName())
                .description(po.getDescription())
                .fileName(po.getFileName())
                .fileType(po.getFileType())
                .ownerType(po.getOwnerType() != null ? OwnerType.valueOf(po.getOwnerType()) : null)
                .ownerId(po.getOwnerId())
                .docCount(po.getDocCount())
                .createdAt(po.getCreatedAt())
                .build();
    }

}
