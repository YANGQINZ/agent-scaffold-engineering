package com.ai.agent.domain.knowledge.service;

import java.util.List;

/**
 * 向量嵌入服务接口
 */
public interface EmbeddingService {

    /**
     * 单文本嵌入
     */
    float[] embed(String text);

    /**
     * 批量文本嵌入
     */
    List<float[]> embedBatch(List<String> texts);

}
