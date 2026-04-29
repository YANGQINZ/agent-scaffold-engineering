package com.ai.agent.api.model.agent;

import lombok.*;

/**
 * 模型配置DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigDTO {

    /** 模型名称 */
    private String name;

    /** 温度参数 */
    private Double temperature;

    /** 最大Token数 */
    private Integer maxTokens;

}
