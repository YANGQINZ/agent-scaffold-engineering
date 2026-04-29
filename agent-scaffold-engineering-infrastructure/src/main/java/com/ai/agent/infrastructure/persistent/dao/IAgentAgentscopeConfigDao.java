package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.AgentAgentscopeConfigPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AgentScope配置DAO
 */
@Mapper
public interface IAgentAgentscopeConfigDao {

    /**
     * 批量插入AgentScope配置
     */
    void batchInsert(List<AgentAgentscopeConfigPO> pos);

    /**
     * 根据智能体定义ID查询AgentScope配置
     */
    List<AgentAgentscopeConfigPO> selectByAgentDefinitionId(Long agentDefinitionId);

    /**
     * 根据智能体定义ID删除AgentScope配置
     */
    void deleteByAgentDefinitionId(Long agentDefinitionId);

}
