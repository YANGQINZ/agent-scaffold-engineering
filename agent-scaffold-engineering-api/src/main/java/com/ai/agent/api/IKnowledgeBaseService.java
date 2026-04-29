package com.ai.agent.api;

import com.ai.agent.api.model.knowledge.dto.DocumentChunkDTO;
import com.ai.agent.api.model.knowledge.dto.KnowledgeBaseResponseDTO;
import com.ai.agent.types.model.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface IKnowledgeBaseService {
    Response<Void> uploadKnowledgeBase(MultipartFile file, String name);

    /**
     * 查询所有知识库列表
     */
    Response<List<KnowledgeBaseResponseDTO>> listKnowledgeBases();

    /**
     * 删除知识库
     */
    Response<Void> deleteKnowledgeBase(String baseId);

    /**
     * 查询知识库下的文档分块列表
     */
    Response<List<DocumentChunkDTO>> listDocuments(String baseId);
}
