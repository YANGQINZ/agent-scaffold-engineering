package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 引擎类型枚举 — 替代原有 AgentMode
 *
 * GRAPH: Spring AI Alibaba StateGraph 引擎，按预定义流程图执行节点任务
 * AGENTSCOPE: agentscope-java Pipeline 引擎，处理多 Agent 动态协作
 * HYBRID: 混合引擎，外层 Graph 编排 + 子节点引擎委托
 */
@Getter
@AllArgsConstructor
public enum EngineType {

    GRAPH("Spring AI Alibaba StateGraph"),
    AGENTSCOPE("agentscope-java Pipeline"),
    HYBRID("外层Graph + 内层引擎委托");

    private final String description;

}
