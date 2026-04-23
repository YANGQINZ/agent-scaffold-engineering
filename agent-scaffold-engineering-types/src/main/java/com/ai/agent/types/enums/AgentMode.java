package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentMode {

    WORKFLOW("Workflow编排"),
    REACT("ReAct Agent"),
    GRAPH("Graph编排"),
    HYBRID("Hybrid混合");

    private final String description;

}
