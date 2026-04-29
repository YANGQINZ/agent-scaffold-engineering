package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.AgentDefinitionPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 智能体定义DAO
 */
@Mapper
public interface IAgentDefinitionDao {

    /**
     * 插入智能体定义
     */
    void insert(AgentDefinitionPO po);

    /**
     * 根据智能体ID查询
     */
    AgentDefinitionPO selectByAgentId(String agentId);

    /**
     * 查询全部智能体定义
     */
    List<AgentDefinitionPO> selectAll();

    /**
     * 根据智能体ID更新
     */
    void updateByAgentId(AgentDefinitionPO po);

    /**
     * 根据智能体ID删除
     */
    void deleteByAgentId(String agentId);

}
