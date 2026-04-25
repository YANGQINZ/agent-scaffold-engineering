package com.ai.agent.domain.knowledge.service;

import com.ai.agent.domain.knowledge.model.entity.RerankItem;

import java.util.List;

/**
 * Reranking精排服务接口 — 对候选文档列表进行语义精排
 */
public interface RerankingService {

    /**
     * 对候选文档列表进行语义精排
     *
     * @param query     查询文本
     * @param documents 候选文档内容列表
     * @param topN      返回Top-N
     * @return 按相关性得分从高到低排列的结果，失败时返回null（降级为RRF结果）
     */
    List<RerankItem> rerank(String query, List<String> documents, int topN);

}
