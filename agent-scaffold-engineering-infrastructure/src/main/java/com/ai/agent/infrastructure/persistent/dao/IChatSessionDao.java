package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.ChatSessionPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

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

    /**
     * 根据用户ID查询会话列表
     */
    List<ChatSessionPO> selectByUserId(String userId);

    /**
     * 根据Agent ID查询会话列表
     */
    List<ChatSessionPO> selectByAgentId(String agentId);

    /**
     * 查询所有会话列表
     */
    List<ChatSessionPO> selectAll();

}
