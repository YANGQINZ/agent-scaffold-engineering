package com.ai.agent.domain.knowledge.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MMR多样性重排配置值对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MmrConfig {

    /** 相关性vs多样性权衡系数（0=最大多样性，1=最大相关性） */
    @Builder.Default
    private Double lambda = 0.7;

    /** 最终返回片段数 */
    @Builder.Default
    private Integer topK = 5;

    /** 是否启用MMR多样性重排 */
    @Builder.Default
    private Boolean enabled = true;

}
