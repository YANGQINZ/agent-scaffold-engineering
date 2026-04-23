package com.ai.agent.domain.knowledge.repository;

import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;

/**
 * 知识库仓储接口
 */
public interface IKnowledgeBaseRepository {

    /**
     * 保存知识库
     */
    void save(KnowledgeBase knowledgeBase);

    /**
     * 根据知识库ID查找
     */
    KnowledgeBase findById(String baseId);

}
