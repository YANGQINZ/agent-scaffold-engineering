package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.ChatSessionPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天会话DAO
 */
@Mapper
public interface IChatSessionDao {

    /**
     * 插入会话
     */
    void insert(ChatSessionPO chatSessionPO);

    /**
     * 根据会话ID查询
     */
    ChatSessionPO selectById(String sessionId);

    /**
     * 更新会话TTL
     */
    void updateTtl(String sessionId);

}
