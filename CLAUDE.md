# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在使用本仓库中的代码时提供指导。

## 构建与运行

```bash

# 完整构建（默认通过 surefire 配置跳过测试）

mvn clean install

# 运行单个测试

mvn test -pl agent-scaffold-engineering-app -Dtest=ApiTest#test -DskipTests=false

# 运行应用程序（需要 AI_DASHSCOPE_API_KEY 环境变量）

AI_DASHSCOPE_API_KEY=your-key mvn spring-boot:run -pl agent-scaffold-engineering-app

```

应用程序运行在 8091 端口，激活配置文件为 `dev`。 PostgreSQL (pgvector) 位于 localhost:5432/agent_db，是主数据库。

## 架构 — DDD 多模块

```

app → trigger, infrastructure (Bootstrap, Spring 配置, test)

trigger → domain, api, types (HTTP 控制器)

domain → types (核心逻辑 — 无基础设施依赖)

infrastructure → domain, types (Repository 实现, MyBatis 映射器, PO 类)

api → types (服务接口 + DTO)

types (枚举, 常量, 异常, Response<T>)

```

### `domain/` 中的三个限界上下文

| 上下文 | 包 | 用途 |

|---------|---------|---------|

| **chat** | `domain.chat` | 聊天管道：ChatFacade → ModeRouter → ChatStrategy（策略模式）。支持同步和流式（Flux）响应。 |

| **代理** | `domain.agent` | 代理组装：AgentRegistry（基于 ConcurrentHashMap）→ AgentDefinition 聚合 → 通过 AgentOrchestratorStrategy 实现 4 个执行器（React/Workflow/Graph/Hybrid）。 |

| **知识** | `domain.knowledge` | RAG 流水线：EmbeddingService → RagService（查询重写 → 混合搜索 [向量 α=0.7 + BM25 β=0.3] → RRF 融合重排序 → 增强提示）。 |

### 关键设计模式

- **策略 + 路由**：`ChatStrategy` 接口与 `ModeRouter` 路由器配合，将 `ChatMode`（SIMPLE/MULTI_TURN/AGENT）路由到具体的策略。

- **装饰器**：`RagDecorator` 包装任何 `ChatStrategy`，以便在 `ragEnabled=true` 时注入 RAG。

- **执行器分发**：`AgentOrchestratorStrategy` 通过 `EnumMap` 将 `AgentMode`（REACT/WORKFLOW/GRAPH/HYBRID）路由到专用的执行器服务。

- **领域中的存储库接口，基础设施中的实现**：例如，`IChatSessionRepository`（领域）→ `ChatSessionRepositoryImpl`（基础设施）。

### 添加新的领域上下文

复制 `domain/src/main/java/com/ai/agent/domain/` 下的现有上下文目录（例如 `chat/`），并重命名包名。每个上下文必须包含：`model/aggregate/`、`model/entity/`、`model/valobj/`、`repository/`（仅限接口）和 `service/`。

## Spring AI 集成

- Spring AI BOM 1.1.4 + Spring AI Alibaba BOM 1.1.2.0 + Extensions BOM 1.1.2.1

- 默认模型：`qwq-plus`，通过 DashScope（阿里巴巴云）实现

- 通过 `spring-ai-starter-mcp-client-webflux` 启用 MCP 客户端

- `spring-ai-alibaba-agent-framework` 提供 `ReactAgent`，用于 `ReactExecutor`

- `spring-ai-agent-utils` 用于领域层

## API 接口

| 方法 | 路径 | 描述 |

|--------|------|-------------|

| POST | `/api/v1/chat` | 同步聊天 |

| POST | `/api/v1/chat/stream` | 流式聊天（通过 Flux 实现 SSE） |

| DELETE | `/api/v1/chat/session/{sessionId}` | 删除会话 |

| POST | `/api/v1/knowledge/bases` | 创建知识库 |

| POST | `/api/v1/knowledge/upload` | 上传知识库文档 |

## 已注释的基础设施

Redis、RocketMQ 和 XXL-Job 的配置存在于 YAML 和代码中，但已被注释掉。需要时请取消注释。Docker Compose 文件 `docs/dev-ops/environment/docker-compose.yml` 会设置所有这些服务以及 pgvector PostgreSQL。

## 约定

- 代码注释、日志消息和异常消息均使用中文——请保持此约定。

- 全文使用 Lombok（`@Data`、`@Builder`、`@Slf4j`、`@RequiredArgsConstructor`）。

- 基础设施中的 PO 类与域实体对应，用于持久化映射。

- MyBatis 映射器 XML 文件位于 `infrastructure/src/main/resources/mybatis/mapper/`。