package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent模式枚举 — 已被 EngineType 替代
 *
 * @deprecated 请使用 {@link EngineType} 替代。保留此枚举仅为兼容 chat 域旧代码，
 *             新代码一律使用 EngineType。
 */
@Getter
@AllArgsConstructor
@Deprecated
public enum AgentMode {

    WORKFLOW("工作流编排"),
    REACT("ReAct推理执行"),
    GRAPH("Graph图编排"),
    HYBRID("混合编排");

    private final String description;

}
