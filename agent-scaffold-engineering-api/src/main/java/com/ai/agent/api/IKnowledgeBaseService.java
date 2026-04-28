package com.ai.agent.api;

import com.ai.agent.api.model.knowledge.KnowledgeBaseResponseDTO;
import com.ai.agent.types.model.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface IKnowledgeBaseService {
    Response<KnowledgeBaseResponseDTO> createKnowledgeBase(String name, String description, String ownerType, String ownerId);
    Response<String> uploadDocument(String knowledgeBaseId, MultipartFile file, String userId);
    Response<Map<String, Object>> uploadKnowledgeBase(MultipartFile file, String name, String category);
}
