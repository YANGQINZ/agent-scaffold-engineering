package com.ai.agent.domain.knowledge.service;

import com.ai.agent.domain.knowledge.model.entity.KnowledgeBaseResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface IKnowledgeService {
    void uploadKnowledgeBase(MultipartFile file, String name);
}
