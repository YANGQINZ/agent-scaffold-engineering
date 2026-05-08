package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import com.ai.agent.domain.agent.repository.IMcpServerConfigRepository;
import com.ai.agent.infrastructure.persistent.dao.IMcpServerConfigDao;
import com.ai.agent.infrastructure.persistent.po.McpServerConfigPO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP Server 配置仓储实现 — 数据库持久化
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class McpServerConfigRepositoryImpl implements IMcpServerConfigRepository {

    private final IMcpServerConfigDao mcpServerConfigDao;
    private final ObjectMapper objectMapper;

    @Override
    public McpServerConfigEntry save(McpServerConfig config, String description) {
        McpServerConfigPO po = toPO(config, description);
        LocalDateTime now = LocalDateTime.now();
        po.setCreatedAt(now);
        po.setUpdatedAt(now);
        mcpServerConfigDao.insert(po);
        log.info("保存MCP配置: name={}", config.getName());
        return toEntry(po);
    }

    @Override
    public void update(Long id, McpServerConfig config, String description) {
        McpServerConfigPO existing = mcpServerConfigDao.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("MCP配置不存在: id=" + id);
        }
        McpServerConfigPO po = toPO(config, description);
        po.setId(id);
        po.setUpdatedAt(LocalDateTime.now());
        mcpServerConfigDao.updateById(po);
        log.info("更新MCP配置: id={}, name={}", id, config.getName());
    }

    @Override
    public void deleteById(Long id) {
        mcpServerConfigDao.deleteById(id);
        log.info("删除MCP配置: id={}", id);
    }

    @Override
    public Optional<McpServerConfigEntry> findById(Long id) {
        McpServerConfigPO po = mcpServerConfigDao.selectById(id);
        return Optional.ofNullable(po).map(this::toEntry);
    }

    @Override
    public Optional<McpServerConfigEntry> findByName(String name) {
        McpServerConfigPO po = mcpServerConfigDao.selectByName(name);
        return Optional.ofNullable(po).map(this::toEntry);
    }

    @Override
    public List<McpServerConfigEntry> findAll() {
        return mcpServerConfigDao.selectAll().stream().map(this::toEntry).toList();
    }

    // ═══════════════════════════════════════════════════════
    // PO ↔ Entry 转换
    // ═══════════════════════════════════════════════════════

    private McpServerConfigPO toPO(McpServerConfig config, String description) {
        McpServerConfigPO po = new McpServerConfigPO();
        po.setName(config.getName());
        po.setTransport(config.getTransport());
        po.setCommand(config.getCommand());
        po.setArgs(toJson(config.getArgs()));
        po.setUrl(config.getUrl());
        po.setHeaders(toJson(config.getHeaders()));
        po.setDescription(description);
        return po;
    }

    private McpServerConfigEntry toEntry(McpServerConfigPO po) {
        return new McpServerConfigEntry(
                po.getId(),
                po.getName(),
                po.getTransport(),
                po.getCommand(),
                parseJsonList(po.getArgs()),
                po.getUrl(),
                parseJsonMap(po.getHeaders()),
                po.getDescription()
        );
    }

    // ═══════════════════════════════════════════════════════
    // JSON 工具方法
    // ═══════════════════════════════════════════════════════

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("JSON列表反序列化失败: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, String> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("JSON对象反序列化失败: {}", e.getMessage());
            return Map.of();
        }
    }

}
