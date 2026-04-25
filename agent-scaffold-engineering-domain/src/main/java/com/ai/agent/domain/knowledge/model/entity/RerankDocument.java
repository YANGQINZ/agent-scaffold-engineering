package com.ai.agent.domain.knowledge.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rerank文档原文实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankDocument {

    /** 文档原文 */
    private String text;

}
