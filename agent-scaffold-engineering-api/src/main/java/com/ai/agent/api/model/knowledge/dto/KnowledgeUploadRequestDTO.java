package com.ai.agent.api.model.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeUploadRequestDTO {
    @NotBlank(message = "knowledgeBaseId不能为空")
    private String knowledgeBaseId;
    private String userId;
}
