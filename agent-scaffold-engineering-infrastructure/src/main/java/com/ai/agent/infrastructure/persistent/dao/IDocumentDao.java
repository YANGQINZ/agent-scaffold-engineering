package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.DocumentPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文档DAO
 */
@Mapper
public interface IDocumentDao {

    /**
     * 插入文档
     */
    void insert(DocumentPO documentPO);

    /**
     * 根据文档ID查询
     */
    DocumentPO selectById(String docId);

    /**
     * 根据知识库ID查询文档列表
     */
    List<DocumentPO> selectByBaseId(String baseId);

    /**
     * 更新文档处理状态
     */
    void updateStatus(String docId, String status);

}
