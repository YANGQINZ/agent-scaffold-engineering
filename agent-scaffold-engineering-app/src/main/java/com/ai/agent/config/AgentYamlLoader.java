package com.ai.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent配置加载器 — Agent定义已改为从数据库加载（由AgentRegistry @PostConstruct完成），
 * 此配置类保留启动日志，YAML加载逻辑已废弃。
 */
@Slf4j
@Configuration
public class AgentYamlLoader {

    @Bean
    public ApplicationRunner agentYamlLoaderRunner() {
        return args -> {
            log.info("Agent定义已由AgentRegistry从数据库加载，YAML加载已跳过");
        };
    }

}
