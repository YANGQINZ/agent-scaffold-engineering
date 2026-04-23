package com.ai.agent.api;

import com.ai.agent.api.model.knowledge.KnowledgeBaseResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface IKnowledgeService {
    KnowledgeBaseResponseDTO createKnowledgeBase(String name, String description, String ownerType, String ownerId);
    String uploadDocument(String knowledgeBaseId, MultipartFile file, String userId);
}
