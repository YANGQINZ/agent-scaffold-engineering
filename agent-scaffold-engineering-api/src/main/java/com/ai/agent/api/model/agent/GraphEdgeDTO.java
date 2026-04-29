package com.ai.agent.api.model.agent;

import lombok.*;

/**
 * 图边DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdgeDTO {

    /** 起始节点 */
    private String from;

    /** 目标节点 */
    private String to;

    /** 条件表达式 */
    private String condition;

}
