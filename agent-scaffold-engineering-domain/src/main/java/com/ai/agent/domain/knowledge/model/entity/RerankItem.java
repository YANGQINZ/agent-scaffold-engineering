package com.ai.agent.domain.knowledge.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rerank排序结果项实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankItem {

    /** 文档原文（returnDocuments=true时返回） */
    private RerankDocument document;

    /** 原始documents列表中的索引位置 */
    private Integer index;

    /** 语义相关性得分，0.0-1.0 */
    private Double relevanceScore;

}
