package com.ai.agent.domain.chat.model.valobj;

import com.ai.agent.types.enums.StreamEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamEvent {

    /** 事件类型 */
    private StreamEventType type;

    /** 事件数据 */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    /** 会话ID */
    private String sessionId;

    /**
     * 文本片段事件
     */
    public static StreamEvent textDelta(String text, String sessionId) {
        Map<String, Object> data = new HashMap<>();
        data.put("text", text);
        return StreamEvent.builder()
                .type(StreamEventType.TEXT_DELTA)
                .data(data)
                .sessionId(sessionId)
                .build();
    }

    /**
     * 思考过程事件
     */
    public static StreamEvent thinking(String thought, String sessionId) {
        Map<String, Object> data = new HashMap<>();
        data.put("thought", thought);
        return StreamEvent.builder()
                .type(StreamEventType.THINKING)
                .data(data)
                .sessionId(sessionId)
                .build();
    }

    /**
     * 节点开始事件
     */
    public static StreamEvent nodeStart(String nodeName, String sessionId) {
        Map<String, Object> data = new HashMap<>();
        data.put("nodeName", nodeName);
        return StreamEvent.builder()
                .type(StreamEventType.NODE_START)
                .data(data)
                .sessionId(sessionId)
                .build();
    }

    /**
     * 节点结束事件
     */
    public static StreamEvent nodeEnd(String nodeName, String sessionId) {
        Map<String, Object> data = new HashMap<>();
        data.put("nodeName", nodeName);
        return StreamEvent.builder()
                .type(StreamEventType.NODE_END)
                .data(data)
                .sessionId(sessionId)
                .build();
    }

    /**
     * RAG检索事件 — 推送检索到的文档片段和来源
     */
    public static StreamEvent ragRetrieve(List<SourceRef> sources, String sessionId) {
        Map<String, Object> data = new HashMap<>();
        data.put("sources", sources);
        return StreamEvent.builder()
                .type(StreamEventType.RAG_RETRIEVE)
                .data(data)
                .sessionId(sessionId)
                .build();
    }

    /**
     * 完成事件
     */
    public static StreamEvent done(Boolean ragDegraded, Map<String, Object> metadata, String sessionId) {
        Map<String, Object> data = new HashMap<>();
        if (ragDegraded != null) {
            data.put("ragDegraded", ragDegraded);
        }
        if (metadata != null) {
            data.putAll(metadata);
        }
        return StreamEvent.builder()
                .type(StreamEventType.DONE)
                .data(data)
                .sessionId(sessionId)
                .build();
    }

}
