package com.ai.agent.domain.knowledge.repository;

import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;

import java.util.List;

/**
 * 知识库仓储接口
 */
public interface IKnowledgeBaseRepository {

    /**
     * 保存知识库
     */
    long save(KnowledgeBase knowledgeBase);

    /**
     * 根据知识库ID查找
     */
    KnowledgeBase findById(String baseId);

    /**
     * 查询所有知识库
     */
    List<KnowledgeBase> findAll();

    /**
     * 根据知识库ID删除
     */
    void deleteById(String baseId);

}
