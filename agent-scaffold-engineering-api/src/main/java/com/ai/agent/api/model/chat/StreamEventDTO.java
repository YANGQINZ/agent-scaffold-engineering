package com.ai.agent.api.model.chat;

import com.ai.agent.types.enums.StreamEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamEventDTO {
    private StreamEventType type;
    private Map<String, Object> data;
    private String sessionId;
}
