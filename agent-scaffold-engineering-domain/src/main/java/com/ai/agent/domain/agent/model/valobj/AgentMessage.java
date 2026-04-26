package com.ai.agent.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一消息值对象 — 跨引擎传递的标准消息格式
 *
 * 包含发送者/接收者标识、消息内容、时间戳和元数据扩展字段。
 * receiverId 为 null 时表示广播消息，对应 AgentScope 的 MsgHub 模式。
 * metadata 可携带来源引擎标识、token 统计、角色信息等。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage {

    /** 消息唯一标识，自动生成 UUID */
    @Builder.Default
    private String messageId = UUID.randomUUID().toString();

    /** 发送者 Agent ID */
    private String senderId;

    /** 接收者 Agent ID，null 表示广播 */
    private String receiverId;

    /** 消息内容 */
    private String content;

    /** 时间戳 */
    private long timestamp;

    /** 扩展字段（来源引擎、token 用量、角色等） */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 从 metadata 中获取指定键的值
     *
     * @param key 键名
     * @return 对应的值，不存在时返回 null
     */
    public Object getMetadataValue(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    /**
     * 向 metadata 中添加键值对
     *
     * @param key   键名
     * @param value 值
     * @return 当前 AgentMessage 实例（链式调用）
     */
    public AgentMessage addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
        return this;
    }

}
