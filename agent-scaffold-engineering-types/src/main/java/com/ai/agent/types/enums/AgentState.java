package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 状态枚举
 *
 * IDLE: 空闲，等待任务分配
 * RUNNING: 执行中，正在处理消息
 * SUSPENDED: 挂起，等待外部输入或检查点暂停
 * ERROR: 错误，执行异常
 */
@Getter
@AllArgsConstructor
public enum AgentState {

    IDLE("空闲"),
    RUNNING("执行中"),
    SUSPENDED("挂起"),
    ERROR("错误");

    private final String description;

}
