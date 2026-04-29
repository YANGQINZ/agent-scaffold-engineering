package com.ai.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Workflow定义便捷封装
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinition {

    /** 入口节点ID */
    private String entry;

    /** 节点列表 */
    private List<WorkflowNode> nodes = List.of();

}
