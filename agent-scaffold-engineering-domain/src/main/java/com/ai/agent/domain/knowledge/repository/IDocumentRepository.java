package com.ai.agent.domain.knowledge.repository;

import com.ai.agent.domain.knowledge.model.entity.Document;
import com.ai.agent.types.enums.DocumentStatus;

import java.util.List;

/**
 * 文档仓储接口
 */
public interface IDocumentRepository {

    /**
     * 保存文档
     */
    void save(Document document);

    /**
     * 根据文档ID查找
     */
    Document findById(String docId);

    /**
     * 根据知识库ID查找文档列表
     */
    List<Document> findByBaseId(String baseId);

    /**
     * 更新文档处理状态
     */
    void updateStatus(String docId, DocumentStatus status);

}
