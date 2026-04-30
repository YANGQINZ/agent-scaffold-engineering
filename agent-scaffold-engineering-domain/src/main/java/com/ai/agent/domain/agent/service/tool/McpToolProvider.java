package com.ai.agent.domain.agent.service.tool;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.entity.AgentscopeAgentConfig;
import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpSyncClientWrapper;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一MCP工具提供者 — 双引擎工具转换入口
 *
 * 管理所有 MCP Server 客户端连接，为 Graph 引擎转换为 FunctionToolCallback，
 * 为 AgentScope 引擎构建 Toolkit。支持运行时动态注册/注销 MCP Server。
 *
 * AgentScope 集成路径：
 * - McpClientBuilder.create(name).stdioTransport()/sseTransport() → McpClientWrapper
 * - Toolkit.registerMcpClient(wrapper) → 将 MCP 工具注册到 Toolkit
 * - ReActAgent.builder().toolkit(toolkit).build() → Agent 可调用 MCP 工具
 */
@Slf4j
@Service
public class McpToolProvider {

    /** 已建立的 MCP 客户端连接（name → wrapper） */
    private final Map<String, McpClientWrapper> clients = new ConcurrentHashMap<>();

    /** MCP Server 配置缓存 */
    private final Map<String, McpServerConfig> serverConfigs = new ConcurrentHashMap<>();

    /**
     * 初始化 MCP 客户端连接
     *
     * @param configs MCP Server 配置列表
     */
    public void init(List<McpServerConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        for (McpServerConfig config : configs) {
            try {
                registerServer(config);
            } catch (Exception e) {
                log.warn("MCP Server初始化失败: name={}, error={}", config.getName(), e.getMessage());
            }
        }
    }

    /**
     * 根据节点级 MCP 配置构建 Spring AI ToolCallback 列表
     *
     * @param configs MCP Server 配置列表
     * @return 所有配置对应的工具回调列表
     */
    public List<ToolCallback> buildGraphTools(List<McpServerConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }
        List<ToolCallback> tools = new ArrayList<>();
        for (McpServerConfig config : configs) {
            try {
                // 确保客户端已初始化
                registerServer(config);
                // 获取该 MCP 服务器的工具
                List<ToolCallback> serverTools = getGraphTools(config.getName());
                if (serverTools != null) {
                    tools.addAll(serverTools);
                }
            } catch (Exception e) {
                log.warn("构建Graph工具失败: server={}, error={}", config.getName(), e.getMessage());
            }
        }
        log.info("构建Graph工具完成: 共{}个MCP Server, 工具数={}", configs.size(), tools.size());
        return tools;
    }

    /**
     * 为 Graph 引擎提供工具：从缓存的 MCP 客户端获取 Spring AI ToolCallback
     *
     * @param serverName MCP Server 名称
     * @return 工具回调列表
     */
    public List<ToolCallback> getGraphTools(String serverName) {
        log.debug("获取Graph工具: serverName={}", serverName);
        McpClientWrapper wrapper = clients.get(serverName);
        if (wrapper == null) {
            log.debug("MCP客户端不存在: serverName={}", serverName);
            return List.of();
        }

        try {
            // 确保客户端已初始化
            if (!wrapper.isInitialized()) {
                wrapper.initialize().block();
            }

            // 从 McpSyncClientWrapper 中提取 McpSyncClient
            McpSyncClient syncClient = extractSyncClient(wrapper);
            if (syncClient == null) {
                log.warn("无法提取McpSyncClient: serverName={}, wrapper类型={}", serverName, wrapper.getClass().getSimpleName());
                return List.of();
            }

            // 使用 Spring AI 工具类获取 ToolCallback 列表
            return McpToolUtils.getToolCallbacksFromSyncClients(List.of(syncClient));
        } catch (Exception e) {
            log.error("获取Graph工具失败: serverName={}, error={}", serverName, e.getMessage());
            return List.of();
        }
    }

    /**
     * 从 McpSyncClientWrapper 中提取底层的 McpSyncClient
     *
     * McpSyncClientWrapper 将 McpSyncClient 作为私有字段存储，
     * 需要通过反射获取以构建 Spring AI ToolCallback。
     *
     * @param wrapper MCP 客户端包装器
     * @return McpSyncClient 实例，提取失败返回 null
     */
    private McpSyncClient extractSyncClient(McpClientWrapper wrapper) {
        if (!(wrapper instanceof McpSyncClientWrapper)) {
            return null;
        }
        try {
            java.lang.reflect.Field clientField = McpSyncClientWrapper.class.getDeclaredField("client");
            clientField.setAccessible(true);
            return (McpSyncClient) clientField.get(wrapper);
        } catch (Exception e) {
            log.error("反射提取McpSyncClient失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 为 AgentScope 引擎构建 Toolkit — 集成 agentscope-java McpClientBuilder
     *
     * 根据配置构建 McpClientWrapper 并注册到 Toolkit 中，
     * 支持 stdio 和 sse 两种传输方式。
     *
     * @param agentConfig 子Agent配置（可为null，表示使用主Agent配置）
     * @param mainDef     主Agent定义
     * @return Toolkit 实例（包含已注册的 MCP 工具）
     */
    public Toolkit buildAgentScopeToolkit(AgentscopeAgentConfig agentConfig, AgentDefinition mainDef) {
        Toolkit toolkit = new Toolkit();

        // 收集需要注册的 MCP Server
        List<McpServerConfig> serversToRegister = new ArrayList<>();

        // 1. 子 Agent 自身的 MCP Server 配置
        if (agentConfig != null && agentConfig.getMcpServers() != null) {
            serversToRegister.addAll(agentConfig.getMcpServers());
        }

        // 2. 主 Agent 的 MCP Server 配置（兜底）
        if (serversToRegister.isEmpty() && mainDef.getMcpServers() != null) {
            serversToRegister.addAll(mainDef.getMcpServers());
        }

        // 3. 构建并注册 McpClientWrapper
        for (McpServerConfig serverConfig : serversToRegister) {
            try {
                McpClientWrapper wrapper = getOrCreateClient(serverConfig);
                if (wrapper != null) {
                    toolkit.registerMcpClient(wrapper).block();
                    log.info("Toolkit注册MCP Server成功: name={}", serverConfig.getName());
                }
            } catch (Exception e) {
                log.warn("Toolkit注册MCP Server失败: name={}, error={}", serverConfig.getName(), e.getMessage());
            }
        }

        // 4. 过滤 enableTools（如果配置了只启用部分工具）
        if (agentConfig != null && agentConfig.getEnableTools() != null && !agentConfig.getEnableTools().isEmpty()) {
            toolkit.setActiveGroups(agentConfig.getEnableTools());
            log.debug("Toolkit设置工具过滤: enableTools={}", agentConfig.getEnableTools());
        }

        return toolkit;
    }

    /**
     * 获取或创建 MCP 客户端连接
     *
     * 使用 agentscope McpClientBuilder 构建客户端，
     * 缓存已建立的连接避免重复初始化。
     *
     * @param config MCP Server 配置
     * @return McpClientWrapper 实例
     */
    private McpClientWrapper getOrCreateClient(McpServerConfig config) {
        // 先检查缓存，避免 computeIfAbsent 中创建失败导致 NPE
        McpClientWrapper existing = clients.get(config.getName());
        if (existing != null) {
            return existing;
        }

        try {
            McpClientBuilder builder = McpClientBuilder.create(config.getName());

            String transport = config.getTransport() != null ? config.getTransport() : "stdio";

            if ("stdio".equalsIgnoreCase(transport)) {
                // Stdio 传输方式
                if (config.getCommand() != null) {
                    List<String> args = config.getArgs() != null ? config.getArgs() : List.of();
                    Map<String, String> env = config.getHeaders() != null ? config.getHeaders() : Map.of();
                    builder.stdioTransport(config.getCommand(), args, env);
                }
            } else if ("sse".equalsIgnoreCase(transport)) {
                // SSE 传输方式
                if (config.getUrl() != null) {
                    builder.sseTransport(config.getUrl());
                }
            } else if ("http".equalsIgnoreCase(transport) || "streamable-http".equalsIgnoreCase(transport)) {
                // Streamable HTTP 传输方式
                if (config.getUrl() != null) {
                    builder.streamableHttpTransport(config.getUrl());
                }
            }

            // 设置 Headers（SSE/HTTP 方式）
            if (config.getHeaders() != null && !"stdio".equalsIgnoreCase(transport)) {
                builder.headers(config.getHeaders());
            }

            // 同步构建客户端
            McpClientWrapper wrapper = builder.buildSync();
            clients.put(config.getName(), wrapper);
            log.info("MCP客户端创建成功: name={}, transport={}", config.getName(), transport);
            return wrapper;

        } catch (Exception e) {
            log.error("MCP客户端创建失败: name={}, error={}", config.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 运行时动态注册 MCP Server
     *
     * @param config MCP Server 配置
     */
    public void registerServer(McpServerConfig config) {
        serverConfigs.put(config.getName(), config);
        // 立即创建 MCP 客户端连接
        getOrCreateClient(config);
        log.info("MCP Server已注册: name={}, transport={}", config.getName(), config.getTransport());
    }

    /**
     * 运行时注销 MCP Server
     *
     * @param name MCP Server 名称
     */
    public void unregisterServer(String name) {
        serverConfigs.remove(name);
        McpClientWrapper client = clients.remove(name);
        if (client != null) {
            try {
                client.close();
                log.info("MCP Server已注销: name={}", name);
            } catch (Exception e) {
                log.warn("MCP Server关闭异常: name={}, error={}", name, e.getMessage());
            }
        }
    }

    /**
     * 获取指定 MCP Server 的配置
     *
     * @param name Server 名称
     * @return 配置对象，不存在时返回 null
     */
    public McpServerConfig getServerConfig(String name) {
        return serverConfigs.get(name);
    }

}
