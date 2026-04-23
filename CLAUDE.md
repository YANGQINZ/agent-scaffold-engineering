# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在使用本仓库中的代码时提供指导。

## 项目概述

这是一个基于 Java 17 DDD（领域驱动设计）的 AI 智能体应用构建框架，基于 Spring Boot 3.4.3，并集成了 Spring AI 和阿里巴巴 DashScope。

## 构建和运行命令

需要环境变量 `AI_DASHSCOPE_API_KEY`。应用运行在 8091 端口，并启用 `dev` 开发环境。

## 架构 — DDD 分层模块

该项目是一个 Maven 多模块反应器，严格遵循 DDD 分层架构。模块依赖关系如下：

```
app → trigger, infrastructure → Bootstrap, Spring config, agent YAML definitions, integration tests
trigger → types, domain, api → HTTP controller
domain → types  → Core business logic: agent assembly pipeline, chat service, tool/MCP/skill integration
infrastructure  → DB/persistence implemented
api → Service interfaces + DTOs (request/response DTOs)
types → Shared enums, constants, exceptions
```

| Module | Purpose |
|--------|---------|
| **app** | Spring Boot entry point (`com.ai.agent.Application`), config classes, application YMLs |
| **trigger** | Inbound adapters — HTTP controllers, MQ listeners |
| **domain** | Core business logic — aggregates, entities, value objects, repository interfaces, domain services |
| **infrastructure** | Outbound adapters — repository implementations, external service clients (empty stub) |
| **api** | Interface contracts — request/response DTOs |
| **types** | Shared — `Constants`, `Response<T>`, `AppException` |

### 关键约定

- **添加新域**：复制 `domain/src/main/java/com/aiagent/domain/` 目录下的模板上下文（`xxx` 或 `yyy`），并重命名包。每个上下文包含：`model/aggregate/`、`model/entity/`、`model/valobj/`、`repository/`（仅接口）、`service/`。

- **仓库模式**：域定义仓库接口；基础架构实现这些接口。

- **配置已注释掉**：Redis、MyBatis、RocketMQ 和 XXL-Job 的配置在代码和 YAML 文件中均存在，但已被注释掉——需要时请取消注释。
## Spring AI Integration

- 使用 Spring AI BOM 1.1.4 + Spring AI Alibaba BOM 1.1.2.0

- 默认模型：通过 DashScope（阿里巴巴云）部署的 `qwq-plus`

- 通过 `spring-ai-starter-mcp-client-webflux` 启用 MCP 客户端

- `ApiTest.java` 演示了带有工具回调、结构化输出和 MCP 的 `ReactAgent`

