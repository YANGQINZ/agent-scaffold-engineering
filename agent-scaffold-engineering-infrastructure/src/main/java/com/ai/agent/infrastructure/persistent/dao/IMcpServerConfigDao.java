package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.McpServerConfigPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MCP Server 配置 DAO
 */
@Mapper
public interface IMcpServerConfigDao {

    /**
     * 插入 MCP 配置
     */
    int insert(McpServerConfigPO po);

    /**
     * 查询所有 MCP 配置
     */
    List<McpServerConfigPO> selectAll();

    /**
     * 根据主键查询
     */
    McpServerConfigPO selectById(Long id);

    /**
     * 根据名称查询
     */
    McpServerConfigPO selectByName(String name);

    /**
     * 根据主键更新
     */
    int updateById(McpServerConfigPO po);

    /**
     * 根据主键删除
     */
    int deleteById(Long id);

}
