package com.ai.agent.domain.knowledge.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Rerank排序结果实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankResult {

    /** 排序结果列表，按相关性得分从高到低排列 */
    private List<RerankItem> results;

    /** Token消耗统计 */
    private RerankUsage usage;

    /** 请求唯一标识 */
    private String requestId;

}
