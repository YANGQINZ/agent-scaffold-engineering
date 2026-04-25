package com.ai.agent.config;

import com.ai.agent.domain.knowledge.model.valobj.RerankConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reranking精排配置属性绑定 — 将application-dev.yml中的rerank前缀配置绑定到RerankConfig值对象
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rerank")
public class RerankConfigProperties {

    /** 是否启用Reranking精排 */
    private Boolean enabled = true;

    /** Rerank模型名称 */
    private String model = "qwen3-rerank";

    /** 返回Top-N文档 */
    private Integer topN = 5;

    /** 重试次数 */
    private Integer retryCount = 3;

    /** 自定义排序任务指令 */
    private String instruct = "Given a web search query, retrieve relevant passages that answer the query.";

    /**
     * 将配置属性映射为领域值对象
     */
    @Bean
    public RerankConfig rerankConfig() {
        return RerankConfig.builder()
                .enabled(enabled)
                .model(model)
                .topN(topN)
                .retryCount(retryCount)
                .instruct(instruct)
                .build();
    }

}
