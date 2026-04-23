package com.ai.agent.api.model.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDTO {
    private String answer;
    private List<SourceDTO> sources;
    private String sessionId;
    private Boolean ragDegraded;
    private Map<String, Object> metadata;
}
