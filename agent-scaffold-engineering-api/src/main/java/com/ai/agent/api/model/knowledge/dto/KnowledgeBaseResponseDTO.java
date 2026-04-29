package com.ai.agent.api.model.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseResponseDTO {
    private String baseId;
    private String name;
    private String description;
    private Integer docCount;
}
