package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DocumentStatus {

    PENDING("待处理"),
    PROCESSING("处理中"),
    COMPLETED("已完成"),
    FAILED("失败");

    private final String description;

}
