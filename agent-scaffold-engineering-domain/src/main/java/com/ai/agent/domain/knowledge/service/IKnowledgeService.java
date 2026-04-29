package com.ai.agent.domain.knowledge.service;

import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;
import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.model.entity.KnowledgeBaseResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface IKnowledgeService {
    void uploadKnowledgeBase(MultipartFile file, String name);

    /**
     * 查询所有知识库
     */
    List<KnowledgeBase> listKnowledgeBases();

    /**
     * 删除知识库（包括关联的文档分块）
     */
    void deleteKnowledgeBase(String baseId);

    /**
     * 查询知识库下的文档分块列表
     */
    List<DocumentChunk> listDocuments(String baseId);

    /**
     * 向已有知识库追加文档
     */
    void uploadDocument(String baseId, MultipartFile file);
}
