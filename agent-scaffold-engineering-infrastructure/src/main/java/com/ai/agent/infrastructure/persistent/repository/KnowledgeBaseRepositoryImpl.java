package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;
import com.ai.agent.domain.knowledge.repository.IKnowledgeBaseRepository;
import com.ai.agent.infrastructure.persistent.dao.IKnowledgeBaseDao;
import com.ai.agent.infrastructure.persistent.po.KnowledgeBasePO;
import com.ai.agent.types.enums.OwnerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 知识库仓储实现
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeBaseRepositoryImpl implements IKnowledgeBaseRepository {

    private final IKnowledgeBaseDao knowledgeBaseDao;

    @Override
    public void save(KnowledgeBase knowledgeBase) {
        KnowledgeBasePO po = new KnowledgeBasePO();
        po.setBaseId(knowledgeBase.getBaseId());
        po.setName(knowledgeBase.getName());
        po.setDescription(knowledgeBase.getDescription());
        po.setOwnerType(knowledgeBase.getOwnerType().name());
        po.setOwnerId(knowledgeBase.getOwnerId());
        po.setDocCount(knowledgeBase.getDocCount());
        po.setCreatedAt(knowledgeBase.getCreatedAt());
        knowledgeBaseDao.insert(po);
    }

    @Override
    public KnowledgeBase findById(String baseId) {
        KnowledgeBasePO po = knowledgeBaseDao.selectById(baseId);
        if (po == null) {
            return null;
        }
        return toKnowledgeBase(po);
    }

    /**
     * KnowledgeBasePO -> KnowledgeBase 领域对象
     */
    private KnowledgeBase toKnowledgeBase(KnowledgeBasePO po) {
        return KnowledgeBase.builder()
                .baseId(po.getBaseId())
                .name(po.getName())
                .description(po.getDescription())
                .ownerType(OwnerType.valueOf(po.getOwnerType()))
                .ownerId(po.getOwnerId())
                .docCount(po.getDocCount())
                .createdAt(po.getCreatedAt())
                .build();
    }

}
