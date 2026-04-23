package com.ai.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Graph边实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdge {

    /** 起始节点ID */
    private String from;

    /** 目标节点ID */
    private String to;

    /** 边条件 */
    private String condition;

}
