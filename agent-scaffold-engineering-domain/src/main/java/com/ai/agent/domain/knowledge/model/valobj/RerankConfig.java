package com.ai.agent.domain.knowledge.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reranking配置值对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankConfig {

    /** 是否启用Reranking精排 */
    private Boolean enabled = true;

    /** Rerank模型名称 */
    private String model = "qwen3-rerank";

    /** 返回Top-N文档 */
    private Integer topN = 5;

    /** 重试次数（认证失败不重试，限流/5xx/超时重试） */
    private Integer retryCount = 3;

}
