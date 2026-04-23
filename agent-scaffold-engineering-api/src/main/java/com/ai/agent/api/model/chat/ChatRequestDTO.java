package com.ai.agent.api.model.chat;

import com.ai.agent.types.enums.AgentMode;
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
    private AgentMode agentMode;
    private Boolean ragEnabled = false;
    private String knowledgeBaseId;
}
