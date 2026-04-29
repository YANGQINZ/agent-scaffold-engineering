package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.KnowledgeBasePO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 知识库DAO
 */
@Mapper
public interface IKnowledgeBaseDao {

    /**
     * 插入知识库
     *
     * @return
     */
    int insert(KnowledgeBasePO knowledgeBasePO);

    /**
     * 根据知识库ID查询
     */
    KnowledgeBasePO selectById(String baseId);

    /**
     * 查询所有知识库
     */
    List<KnowledgeBasePO> selectAll();

    /**
     * 根据知识库ID删除
     */
    int deleteById(String baseId);

}
