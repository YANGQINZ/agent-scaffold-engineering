package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.ChatMessagePO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 聊天消息DAO
 */
@Mapper
public interface IChatMessageDao {

    /**
     * 插入消息
     */
    void insert(ChatMessagePO chatMessagePO);

    /**
     * 根据会话ID查询消息列表
     */
    List<ChatMessagePO> selectBySessionId(String sessionId);

}
