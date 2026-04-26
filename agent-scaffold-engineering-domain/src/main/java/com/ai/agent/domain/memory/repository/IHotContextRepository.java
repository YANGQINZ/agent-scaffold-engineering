package com.ai.agent.domain.memory.repository;

import com.ai.agent.domain.memory.model.valobj.HotContext;

/**
 * 热层 Redis 操作接口
 */
public interface IHotContextRepository {

    /**
     * 加载热层上下文（Redis miss → DB fallback → 写回）
     */
    HotContext load(String sessionId);

    /**
     * 保存热层上下文
     */
    void save(HotContext context);

    /**
     * 删除热层上下文
     */
    void delete(String sessionId);

    /**
     * 判断是否存在
     */
    boolean exists(String sessionId);
}
