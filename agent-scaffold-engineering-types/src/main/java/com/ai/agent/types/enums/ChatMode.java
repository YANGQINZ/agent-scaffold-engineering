package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChatMode {

    SIMPLE("单轮对话"),
    MULTI_TURN("多轮对话"),
    AGENT("多智能体协同");

    private final String description;

}
