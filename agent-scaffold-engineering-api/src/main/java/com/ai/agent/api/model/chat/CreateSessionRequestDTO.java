package com.ai.agent.api.model.chat;

import com.ai.agent.types.enums.ChatMode;
import com.ai.agent.types.enums.EngineType;
import lombok.Data;

/**
 * 创建会话请求 DTO
 */
@Data
public class CreateSessionRequestDTO {
    /** 会话名称 */
    private String name;
    /** 关联的 Agent 定义 ID（专家模式） */
    private String agentId;
    /** 对话模式，默认 MULTI_TURN */
    private ChatMode mode;
    /** 引擎类型，默认 CHAT */
    private EngineType engine;
}
