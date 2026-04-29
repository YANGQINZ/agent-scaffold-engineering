package com.ai.agent.api;

import com.ai.agent.api.model.knowledge.dto.KnowledgeBaseResponseDTO;
import com.ai.agent.types.model.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface IKnowledgeBaseService {
    Response<Void> uploadKnowledgeBase(MultipartFile file, String name);
}
