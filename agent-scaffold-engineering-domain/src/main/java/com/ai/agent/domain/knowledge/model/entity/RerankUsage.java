package com.ai.agent.domain.knowledge.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rerank API Token消耗统计实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankUsage {

    /** 消耗的Token总数 */
    private Integer totalTokens;

}
