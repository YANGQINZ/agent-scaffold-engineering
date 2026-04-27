package com.ai.agent.domain.agent.model.aggregate;

import com.ai.agent.types.enums.EngineType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * CHAT 引擎 Agent 定义 — 直接 ChatModel 调用，无编排
 *
 * 适用于简单的对话助手、子Agent（分类器、搜索器等）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class ChatAgentDefinition extends AgentDefinition {

    @Override
    public EngineType getEngine() {
        return EngineType.CHAT;
    }

    @Override
    public void setEngine(EngineType engine) {
        if (engine != EngineType.CHAT) {
            throw new IllegalArgumentException("ChatAgentDefinition 的 engine 必须为 CHAT");
        }
        super.setEngine(engine);
    }

}
