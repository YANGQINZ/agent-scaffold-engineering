package com.ai.agent.api.model.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceDTO {
    private String docName;
    private String chunkContent;
    private Double score;
}
