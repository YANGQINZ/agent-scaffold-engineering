package com.ai.agent.config;

import com.ai.agent.domain.knowledge.model.valobj.MmrConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MMR多样性重排配置属性绑定 — 将application-dev.yml中的mmr前缀配置绑定到MmrConfig值对象
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mmr")
public class MmrConfigProperties {

    /** 是否启用MMR多样性重排 */
    private Boolean enabled = true;

    /** 相关性vs多样性权衡系数（0=最大多样性，1=最大相关性） */
    private Double lambda = 0.7;

    /** 最终返回片段数 */
    private Integer topK = 5;

    /**
     * 将配置属性映射为领域值对象
     */
    @Bean
    public MmrConfig mmrConfig() {
        return MmrConfig.builder()
                .enabled(enabled)
                .lambda(lambda)
                .topK(topK)
                .build();
    }

}
