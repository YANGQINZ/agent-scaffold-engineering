package com.ai.agent.domain.knowledge.service;

import com.ai.agent.domain.knowledge.model.entity.KnowledgeBaseResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface IKnowledgeService {
    KnowledgeBaseResponse createKnowledgeBase(String name, String description, String ownerType, String ownerId);
    String uploadDocument(String knowledgeBaseId, MultipartFile file, String userId);

    Map<String, Object> uploadKnowledgeBase(MultipartFile file, String name, String category);
}
