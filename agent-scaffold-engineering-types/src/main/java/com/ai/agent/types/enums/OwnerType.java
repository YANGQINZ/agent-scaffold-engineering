package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OwnerType {

    ADMIN("管理员"),
    USER("用户");

    private final String description;

}
