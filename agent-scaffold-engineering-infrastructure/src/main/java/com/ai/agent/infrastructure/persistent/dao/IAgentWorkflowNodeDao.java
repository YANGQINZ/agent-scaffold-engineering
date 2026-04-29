package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.AgentWorkflowNodePO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 工作流节点DAO
 */
@Mapper
public interface IAgentWorkflowNodeDao {

    /**
     * 批量插入工作流节点
     */
    void batchInsert(List<AgentWorkflowNodePO> pos);

    /**
     * 根据智能体定义ID查询工作流节点
     */
    List<AgentWorkflowNodePO> selectByAgentDefinitionId(Long agentDefinitionId);

    /**
     * 根据智能体定义ID删除工作流节点
     */
    void deleteByAgentDefinitionId(Long agentDefinitionId);

}
