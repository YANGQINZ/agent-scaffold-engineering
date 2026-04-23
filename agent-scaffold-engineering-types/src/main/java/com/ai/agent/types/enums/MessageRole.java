package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MessageRole {

    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system");

    private final String value;

}
