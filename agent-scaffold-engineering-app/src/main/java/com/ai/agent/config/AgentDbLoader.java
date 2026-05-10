package com.ai.agent.config;

import com.ai.agent.domain.agent.service.AgentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Configuration;

/**
 * Agent数据库加载器 — 从数据库加载Agent定义到AgentRegistry
 * <p>
 * 执行顺序：order=2，在AgentYamlLoader（order=1）之后执行。
 * 数据库定义覆盖相同agentId的YAML定义（数据库优先）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AgentDbLoader implements ApplicationRunner, Ordered {

    private final AgentRegistry agentRegistry;

    @Override
    public int getOrder() {
        return 2;
    }

    @Override
    public void run(ApplicationArguments args) {
        agentRegistry.loadFromRepository();
    }

}
