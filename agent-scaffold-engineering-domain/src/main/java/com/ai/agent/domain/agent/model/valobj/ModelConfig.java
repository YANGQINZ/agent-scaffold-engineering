package com.ai.agent.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型配置值对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfig {

    /** 模型名称 */
    private String name = "qwq-plus";

    /** 温度参数 */
    private Double temperature = 0.7;

    /** 最大Token数 */
    private Integer maxTokens = 2000;

}
