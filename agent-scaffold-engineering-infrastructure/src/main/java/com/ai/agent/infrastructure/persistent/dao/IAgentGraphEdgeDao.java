package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.AgentGraphEdgePO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 图边DAO
 */
@Mapper
public interface IAgentGraphEdgeDao {

    /**
     * 批量插入图边
     */
    void batchInsert(List<AgentGraphEdgePO> pos);

    /**
     * 根据智能体定义ID查询图边
     */
    List<AgentGraphEdgePO> selectByAgentDefinitionId(Long agentDefinitionId);

    /**
     * 根据智能体定义ID删除图边
     */
    void deleteByAgentDefinitionId(Long agentDefinitionId);

}
