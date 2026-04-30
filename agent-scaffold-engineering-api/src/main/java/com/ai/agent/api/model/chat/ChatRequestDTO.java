package com.ai.agent.api.model.chat;

import com.ai.agent.api.model.agent.AgentDefinitionDTO;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.enums.ChatMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatRequestDTO {
    @NotBlank(message = "userId不能为空")
    private String userId;
    private String sessionId;
    @NotBlank(message = "query不能为空")
    private String query;
    @NotNull(message = "mode不能为空")
    private ChatMode mode;
    private EngineType engine;
    private Boolean ragEnabled = false;
    /** 是否启用思考过程输出 */
    private Boolean enableThinking = false;
    private String knowledgeBaseId;
    /** 指定目标Agent（AGENT模式下使用） */
    private String agentId;
    /** 内联 Agent 定义（未保存画布时使用） */
    private AgentDefinitionDTO agentDefinition;
    /** 测试运行标识 — 为 true 时跳过上下文注入和历史消息追加 */
    private Boolean testRun = false;
}
