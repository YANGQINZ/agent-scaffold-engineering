# Agent Scaffold Engineering

> 基于 DDD 架构的智能体编排与对话平台，集成多引擎 Agent 编排、RAG 检索增强生成、MCP 工具协议与双层记忆系统。

---

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 核心功能](#2-核心功能)
- [3. 技术栈](#3-技术栈)
- [4. 项目结构](#4-项目结构)
- [5. 核心功能详解](#5-核心功能详解)
- [6. 入门指南](#6-入门指南)

---

## 1. 项目概述

### 目标

Agent Scaffold Engineering 是一套**智能体编排与对话开发平台**，旨在解决以下核心问题：

- **AI Agent 开发门槛高**：从零搭建一个支持多轮对话、工具调用、知识检索的 AI 助手，需要整合 LLM API、向量数据库、文档解析、上下文管理等众多组件，开发周期长且容易出错。
- **Agent 编排复杂**：生产级 AI 应用往往需要多个 Agent 协作完成复杂任务（如研究分析、客服流转），缺乏统一、可视化的编排方式。
- **知识注入与检索困难**：如何将企业私域知识高效地注入 AI 对话流程，并保证检索结果的相关性和多样性，是 RAG 系统的长期痛点。

### 目标用户

- 需要快速搭建 AI 对话应用的开发者
- 需要可视化编排多 Agent 协作流程的 AI 工程师
- 需要将企业知识库接入 AI 系统的团队

### 应用场景

| 场景 | 描述 |
|------|------|
| 智能客服 | 通过 Graph 引擎编排多节点流转，自动路由至不同专业 Agent |
| 研究分析 | AgentScope 引擎驱动多 Agent 动态协作，完成深度研究任务 |
| 企业知识问答 | RAG 管线将文档向量化、检索、精排后注入对话上下文 |
| FAQ 应答 | Simple Chat 模式快速接入，支持 MCP 工具扩展 |

### 预期成果

- 🎯 零代码或低代码配置即可创建具备对话、工具调用、知识检索能力的 AI Agent
- 🎯 可视化画布编排多 Agent 协作流程（Graph / AgentScope / Hybrid 引擎）
- 🎯 开箱即用的 RAG 管线（文档解析 → 向量化 → 混合检索 → Rerank 精排 → MMR 多样性重排）
- 🎯 统一的 MCP Server 工具管理，支持 stdio / SSE / Streamable HTTP 三种传输协议

---

## 2. 核心功能

### 2.1 多模式智能对话

支持三种对话模式，通过策略模式（`ModeRouter`）路由：

- **SIMPLE**：单轮直接调用 LLM，适合 FAQ、简单问答场景
- **MULTI_TURN**：多轮对话模式，自动维护会话上下文与记忆系统，支持思考过程输出（`enableThinking`）
- **AGENT**：Agent 编排模式，根据 `engine` 字段路由至 Graph / AgentScope / Hybrid 引擎执行

### 2.2 多引擎 Agent 编排

通过 `EngineAdapter` 接口实现双引擎架构，`TaskRuntime` 统一调度：

- **CHAT 引擎**：直接 ChatModel 调用，无编排逻辑
- **GRAPH 引擎**：基于 Spring AI Alibaba StateGraph，按预定义有向图（节点 + 边）执行任务流转，支持条件分支
- **AGENTSCOPE 引擎**：基于 agentscope-java Pipeline，处理多 Agent 动态协作（如顺序管道、分工协作）
- **HYBRID 引擎**：外层 Graph 编排 + 子节点可委托给 CHAT / AGENTSCOPE 等内层引擎，兼顾结构化流程与灵活委托

### 2.3 RAG 检索增强生成

四阶段 RAG 管线，确保检索质量：

1. **查询改写**：通过 LLM 将非正式查询转为结构化搜索词
2. **混合检索 + 融合重排**：向量搜索（pgvector HNSW） + BM25 全文检索，RRF 倒数融合排名
3. **Reranking 精排**：调用阿里云百炼 Rerank API（qwen3-rerank 模型），语义级精排，失败自动降级
4. **MMR 多样性重排**：基于 Maximal Marginal Relevance 算法消除冗余结果，保证答案覆盖面

### 2.4 知识库管理

- 上传文档（PDF / Word / TXT 等，通过 Apache Tika 解析）自动切分为 Token 分块
- TokenTextSplitter 分块后向量化存储至 pgvector
- 支持向已有知识库追加文档
- 支持查看文档分块列表、删除知识库（级联删除分块与向量）

### 2.5 MCP Server 工具管理

- 支持三种 MCP 传输协议：`stdio`（本地进程）、`sse`（Server-Sent Events）、`streamableHttp`（流式 HTTP）
- 独立的 MCP 配置 CRUD 管理，可供 Agent 节点引用
- 节点级 MCP 工具配置，不同 Agent 节点可绑定不同工具集

### 2.6 双层记忆系统

- **热层（Hot Context）**：基于 Redis 存储最近 N 条消息，快速组装对话上下文，TTL 可配置
- **冷层（Memory Item）**：基于 PostgreSQL + pgvector 存储长期记忆，支持语义相似度检索
- **记忆提取**：自动从对话中提取事实性记忆，标注重要性与标签
- **记忆压缩**：当上下文超过阈值时，自动压缩早期记忆，保留关键信息
- **上下文组装**：合并热层消息 + 冷层检索结果，注入 LLM Prompt

### 2.7 可视化画布编排

前端基于 `@xyflow/react` 提供可视化 Agent 编排画布：

- 拖拽创建 Agent 节点、起始节点、终止节点
- 可视化连线定义节点间流转关系与条件分支
- 节点属性面板配置：Agent 指令、模型参数、MCP 工具、RAG 开关
- 内联测试运行（`testRun=true`），无需保存即可测试 Agent 流程

### 2.8 流式对话（SSE）

- 基于 Reactor `Flux<StreamEvent>` 实现服务端推送事件流
- 事件类型：`TEXT_DELTA`（文本片段）、`THINKING`（思考过程）、`NODE_START/END`（节点执行）、`RAG_RETRIEVE`（RAG 检索）、`DONE`（完成）
- 前端通过 `EventSource` 实时渲染流式输出，含思考过程折叠展示

### 2.9 多语言与国际化

- 后端中文注释与日志，前端支持中英双语切换（i18next + react-i18next）

---

## 3. 技术栈

### 3.1 后端技术栈

| 技术 | 版本 | 说明 | 选择原因 |
|------|------|------|----------|
| Java | 17 | 编程语言 | LTS 版本，支持 Records、Sealed Classes 等现代语法 |
| Spring Boot | 3.4.3 | 应用框架 | 成熟生态、自动配置、生产就绪 |
| Spring AI | 1.1.4 | AI 编排框架 | 原生支持 ChatModel、VectorStore、RAG 管线 |
| Spring AI Alibaba | 1.1.2.0 | 阿里云 AI 扩展 | DashScope 模型集成、StateGraph 编排引擎 |
| MyBatis | 3.0.4 | ORM 框架 | 灵活 SQL 控制，配合 pgvector 自定义查询 |
| PostgreSQL + pgvector | - | 向量数据库 | 成熟关系型数据库 + 向量检索扩展，支持 HNSW 索引 |
| Redis (Redisson) | - | 缓存/热层存储 | 高性能内存存储，用于热层上下文缓存 |
| Apache Tika | 3.1.0 | 文档解析 | 支持 PDF / Word / TXT 等 50+ 文档格式 |
| Reactor Core | - | 响应式编程 | 流式对话事件推送，Flux/Mono 异步处理 |
| Lombok | - | 代码简化 | 自动生成 getter/setter/builder，减少样板代码 |
| Flyway | - | 数据库迁移 | SQL 版本化管理，保证 schema 一致性 |
| MCP Client (WebFlux) | - | MCP 协议客户端 | 标准化 AI 工具调用协议 |

### 3.2 前端技术栈

| 技术 | 版本 | 说明 | 选择原因 |
|------|------|------|----------|
| React | 19.2 | UI 框架 | 组件化开发、丰富生态 |
| TypeScript | 6.0 | 类型安全 | 编译时类型检查，减少运行时错误 |
| Vite | 8.0 | 构建工具 | 极速 HMR、原生 ESM 支持 |
| Tailwind CSS | 4.2 | 原子化 CSS | 高效样式开发，与 shadcn/ui 完美配合 |
| shadcn/ui | 4.6 | UI 组件库 | 高质量、可定制的 React 组件 |
| @xyflow/react | 12.10 | 画布引擎 | 专业的节点图编辑器，用于 Agent 编排画布 |
| Zustand | 5.0 | 状态管理 | 轻量级、无样板代码的状态管理方案 |
| Axios | 1.15 | HTTP 客户端 | 请求拦截、响应转换、错误处理 |
| i18next | 26.0 | 国际化 | 成熟的 React 国际化方案 |
| React Router | 7.14 | 路由 | 声明式路由，懒加载支持 |

### 3.3 技术交互方式

```
┌────────────┐     SSE/REST      ┌──────────────────────┐     SQL      ┌──────────────┐
│  Frontend  │ ◄──────────────► │  Spring Boot Backend  │ ◄──────────► │  PostgreSQL   │
│  (React)   │                   │  (Port 8091)          │              │  (pgvector)   │
└────────────┘                   └──────────┬───────────┘              └──────────────┘
                                            │
                                            │ Redis SDK
                                            ▼
                                  ┌──────────────────┐     HTTP      ┌──────────────────┐
                                  │  Redis (Hot层)    │              │  DashScope API    │
                                  │  (Context Cache)  │              │  (LLM / Embedding │
                                  └──────────────────┘              │   / Rerank)       │
                                                                    └──────────────────┘
```

- 前端通过 REST API（同步）和 SSE（流式）与后端交互
- 后端通过 Spring AI 调用阿里云 DashScope API（Chat / Embedding / Rerank）
- 向量数据与业务数据统一存储在 PostgreSQL + pgvector
- 热层上下文通过 Redis 缓存，冷层记忆持久化至 PostgreSQL

---

## 4. 项目结构

### 4.1 整体目录树

```
agent-scaffold-engineering/
├── agent-scaffold-engineering-app/          # 🚀 启动模块（Bootstrap + 配置 + 测试）
│   ├── src/main/java/.../config/            #    Spring 配置类（CORS、线程池、Redis、Embedding 等）
│   ├── src/main/resources/
│   │   ├── agents/                          #    Agent YAML 定义文件
│   │   │   ├── chat/                        #      对话类 Agent（classifier、faq-bot、summarizer 等）
│   │   │   ├── agentscope/                  #      AgentScope 类 Agent（research-team）
│   │   │   ├── graph/                       #      Graph 类 Agent（customer-service）
│   │   │   └── hybrid/                      #      Hybrid 类 Agent（research-hybrid）
│   │   ├── mybatis/                         #    MyBatis 配置与 Mapper XML
│   │   ├── sql/                             #    数据库初始化脚本
│   │   └── application-*.yml                #    多环境配置（dev / test / prod）
│   └── Dockerfile
├── agent-scaffold-engineering-trigger/      # 🌐 触发器层（HTTP 控制器）
│   └── src/main/java/.../trigger/
│       ├── http/                            #    REST Controller
│       │   ├── AgentController.java         #      Agent 定义 CRUD
│       │   ├── ChatController.java          #      对话（同步 + 流式）
│       │   ├── SessionController.java       #      会话历史查询
│       │   ├── KnowledgeController.java     #      知识库管理
│       │   ├── McpServerConfigController.java #  MCP 配置管理
│       │   └── GlobalExceptionHandler.java  #      全局异常处理
│       └── converter/                       #    DTO ↔ 领域对象转换器
├── agent-scaffold-engineering-domain/       # 📦 核心领域层（零基础设施依赖）
│   └── src/main/java/.../domain/
│       ├── agent/                           #    Agent 限界上下文
│       │   ├── model/aggregate/             #      聚合根（AgentDefinition、SessionContext 等）
│       │   ├── model/entity/                #      实体（WorkflowNode、GraphEdge、McpServerConfig）
│       │   ├── model/valobj/                #      值对象（ModelConfig、AgentMessage）
│       │   ├── repository/                  #      仓储接口（IAgentDefinitionRepository 等）
│       │   ├── service/                     #      领域服务
│       │   │   ├── AgentRegistry.java       #        Agent 定义注册中心
│       │   │   ├── TaskRuntime.java         #        统一调度入口（实现 ChatStrategy）
│       │   │   ├── ContextStoreFactory.java #        上下文存储工厂
│       │   │   ├── adapter/                 #        引擎适配器（EngineAdapter 接口及实现）
│       │   │   ├── engine/                  #        引擎通道（Graph/AgentScope/Hybrid）
│       │   │   └── tool/                    #        MCP 工具提供者
│       │   └── tools/                       #      内置工具（WeatherTool、RouteSearchTool、FaqTool）
│       ├── chat/                            #    Chat 限界上下文
│       │   ├── model/aggregate/             #      聚合根（ChatSession）
│       │   ├── model/entity/                #      实体（ChatMessage）
│       │   ├── repository/                  #      仓储接口（IChatSessionRepository）
│       │   └── service/                     #      领域服务
│       │       ├── ChatFacade.java          #        聊天门面（统一入口 + RAG 装饰）
│       │       ├── ModeRouter.java          #        对话模式路由器
│       │       └── strategy/                #        策略实现
│       │           ├── SimpleChatStrategy.java    #  单轮对话
│       │           ├── MultiTurnChatStrategy.java #  多轮对话
│       │           └── RagDecorator.java          #  RAG 装饰器
│       ├── knowledge/                       #    Knowledge 限界上下文
│       │   ├── model/aggregate/             #      聚合根（KnowledgeBase）
│       │   ├── model/entity/                #      实体（DocumentChunk、RerankResult 等）
│       │   ├── model/valobj/                #      值对象（RagResult、RerankConfig、MmrConfig）
│       │   ├── repository/                  #      仓储接口（IKnowledgeBaseRepository 等）
│       │   ├── service/                     #      领域服务
│       │   │   ├── IKnowledgeService.java   #        知识库服务接口
│       │   │   ├── business/                #        业务实现
│       │   │   ├── rag/                     #        RAG 管线（RagService、NodeRagService）
│       │   │   ├── mmr/                     #        MMR 多样性重排
│       │   │   ├── EmbeddingService.java    #        向量嵌入服务
│       │   │   └── RerankingService.java    #        Rerank 精排接口
│       │   └── DocumentProcessor.java       #      文档解析与切分
│       ├── memory/                          #    Memory 限界上下文
│       │   ├── model/aggregate/             #      聚合根（MemoryContext）
│       │   ├── model/entity/                #      实体（MemoryItem）
│       │   ├── model/valobj/                #      值对象（HotContext）
│       │   ├── repository/                  #      仓储接口（IMemoryItemRepository 等）
│       │   └── service/                     #      领域服务
│       │       ├── MemoryFacade.java        #        记忆门面
│       │       ├── ContextAssembler.java    #        上下文组装
│       │       ├── ContextCompressor.java   #        上下文压缩
│       │       └── MemoryExtractor.java     #        记忆提取
│       └── common/                          #    跨上下文共享
│           ├── interface_/                  #      接口（ChatStrategy、ContextStore、MemoryPort）
│           ├── valobj/                      #      值对象（ChatRequest、ChatResponse、StreamEvent）
│           └── event/                       #      领域事件（MessageCreatedEvent）
├── agent-scaffold-engineering-infrastructure/ # 🔧 基础设施层
│   └── src/main/java/.../infrastructure/
│       ├── persistent/
│       │   ├── dao/                         #    MyBatis Mapper 接口（@Mapper）
│       │   ├── po/                          #    持久化对象（PO）
│       │   └── repository/                  #    Repository 实现（实现 domain 层接口）
│       └── rerank/
│           └── RerankApiClient.java         #    Rerank API 客户端
│   └── src/main/resources/db/migration/     #    Flyway SQL 迁移脚本
├── agent-scaffold-engineering-api/          # 📡 服务接口层（接口契约 + DTO）
│   └── src/main/java/.../api/
│       ├── IChatService.java                #    对话服务接口
│       ├── IKnowledgeBaseService.java       #    知识库服务接口
│       ├── IMcpServerConfigService.java     #    MCP 配置服务接口
│       └── model/                           #    DTO 定义
│           ├── agent/                       #      Agent 相关 DTO
│           ├── chat/                        #      Chat 相关 DTO
│           └── knowledge/                   #      Knowledge 相关 DTO
├── agent-scaffold-engineering-types/        # 📐 类型定义层（枚举、异常、常量）
│   └── src/main/java/.../types/
│       ├── enums/                           #    枚举（ChatMode、EngineType、AgentState 等）
│       ├── exception/                       #    异常类（ChatException、AgentException 等）
│       ├── model/                           #    通用模型（Response<T>）
│       └── common/                          #    常量
├── frontend/                                # 🖥️ 前端应用
│   └── src/
│       ├── api/                             #    API 调用层（agent、chat、knowledge、mcp）
│       ├── features/
│       │   ├── canvas/                      #      Agent 画布编排
│       │   │   ├── nodes/                   #        画布节点组件（AgentNode、StartNode、EndNode）
│       │   │   ├── edges/                   #        画布边组件（ConditionEdge、DefaultEdge）
│       │   │   ├── AgentCanvas.tsx          #        画布主组件
│       │   │   ├── NodeEditPanel.tsx        #        节点属性编辑面板
│       │   │   └── McpServerForm.tsx        #        MCP 配置表单
│       │   ├── chat/                        #      对话界面
│       │   │   ├── ChatPanel.tsx            #        对话面板
│       │   │   ├── MessageBubble.tsx        #        消息气泡（含思考过程折叠）
│       │   │   ├── ChatInput.tsx            #        输入框
│       │   │   ├── AgentSelector.tsx        #        Agent 选择器
│       │   │   ├── SessionSidebar.tsx       #        会话侧边栏
│       │   │   └── ThinkingBlock.tsx        #        思考过程展示
│       │   ├── knowledge/                   #      知识库管理
│       │   │   ├── KnowledgeBaseList.tsx    #        知识库列表
│       │   │   ├── CreateBaseDialog.tsx     #        创建知识库对话框
│       │   │   └── DocumentList.tsx         #        文档列表
│       │   └── mcp/                         #      MCP 配置管理
│       ├── hooks/                           #    自定义 Hooks（useAgent、useSSE）
│       ├── i18n/                            #    国际化（zh.ts、en.ts）
│       └── components/ui/                   #    通用 UI 组件（shadcn/ui）
└── docs/                                    # 📚 项目文档
    ├── 05-TEST-QA/                          #    测试与质量保证
    ├── 09-PLANNING/                         #    规划与任务管理
    ├── 10-WALKTHROUGH/                      #    变更记录
    └── 11-REFERENCE/                        #    开发规范参考
```

### 4.2 架构模式

本项目采用 **DDD（领域驱动设计）分层架构**，严格遵循依赖规则：

```
trigger（控制器层） ──► api（接口契约层） ──► domain（核心领域层） ◄── infrastructure（基础设施层）
                                                       │
                                                  types（类型定义层）
```

- **领域层零基础设施依赖**：`domain` 模块不依赖 Spring、MyBatis、Redis 等任何基础设施框架
- **依赖倒置**：`domain` 层定义 Repository 接口，`infrastructure` 层提供实现
- **限界上下文隔离**：domain 下有四个限界上下文（chat / agent / knowledge / memory），跨上下文调用通过 Facade 或 Service 接口
- **策略模式路由**：`ModeRouter` 根据 `ChatMode` 路由到对应 `ChatStrategy` 实现，新增模式只需实现接口并注册

---

## 5. 核心功能详解

### 5.1 对话系统

#### 输入

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | String | ✅ | 用户标识 |
| query | String | ✅ | 用户查询内容 |
| mode | ChatMode | ✅ | 对话模式：`SIMPLE` / `MULTI_TURN` / `AGENT` |
| sessionId | String | ❌ | 会话ID（为空时自动生成） |
| engine | EngineType | ❌ | 引擎类型（AGENT 模式下必填） |
| ragEnabled | Boolean | ❌ | 是否启用 RAG（默认 false） |
| knowledgeBaseId | String | ❌ | 知识库ID（ragEnabled=true 时使用） |
| enableThinking | Boolean | ❌ | 是否输出思考过程（默认 false） |
| agentId | String | ❌ | Agent 定义ID（AGENT 模式下使用） |
| agentDefinition | AgentDefinitionDTO | ❌ | 内联 Agent 定义（未保存画布时使用） |
| testRun | Boolean | ❌ | 测试运行标识（默认 false） |

#### 输出

**同步响应**（`ChatResponseDTO`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| answer | String | AI 回答内容 |
| sessionId | String | 会话ID |
| sources | List\<SourceDTO\> | RAG 引用来源（文档名 + 分块内容 + 相关度分数） |
| ragDegraded | Boolean | RAG 是否降级（Rerank 失败时为 true） |
| thinkingContent | String | 思考过程内容（enableThinking=true 时有值） |
| metadata | Map | 额外元数据 |

**流式响应**（SSE，`StreamEventDTO`）：

| 事件类型 | 说明 |
|----------|------|
| TEXT_DELTA | 文本片段增量 |
| THINKING | 思考过程片段 |
| NODE_START / NODE_END | Graph 节点执行开始/结束 |
| RAG_RETRIEVE | RAG 检索结果 |
| DONE | 流式完成 |

#### 工作流程

```
用户请求 → ChatController
    → ChatFacade.chat()
        → ModeRouter.route(mode)          # 策略路由
            ├── SIMPLE  → SimpleChatStrategy     # 单轮 LLM 调用
            ├── MULTI_TURN → MultiTurnChatStrategy # 多轮对话 + 记忆上下文
            └── AGENT → TaskRuntime              # Agent 编排执行
        → RagDecorator（ragEnabled=true 时装饰）  # RAG 检索增强
        → MessageCreatedEvent 发布               # 异步持久化消息
    → Response 封装返回
```

#### 错误处理

| 错误码 | 说明 | 触发条件 |
|--------|------|----------|
| 1001 | 不支持的对话模式 | mode 参数不在枚举范围内 |
| 1002 | 服务繁忙请稍后重试 | LLM API 调用失败 |
| 1003 | 未找到 Agent 定义 | agentId 不存在且未提供内联定义 |
| 1003 | Agent 编排执行失败 | 引擎执行过程中异常 |

---

### 5.2 Agent 编排系统

#### Agent 定义

Agent 定义支持四种引擎类型，通过 YAML 文件或 API 动态注册：

| 引擎类型 | 类名 | 说明 |
|----------|------|------|
| CHAT | `ChatAgentDefinition` | 直接 ChatModel 调用 |
| GRAPH | `GraphAgentDefinition` | 有向图编排（节点 + 边） |
| AGENTSCOPE | `AgentscopeAgentDefinition` | 多 Agent 动态协作 |
| HYBRID | `HybridAgentDefinition` | Graph + 子引擎委托 |

#### 编排执行流程

```
ChatFacade → TaskRuntime.execute()
    → resolveAgentDefinition()           # 三级回退：agentId → 内联定义 → 异常
    → routeAdapter(engineType)           # 路由至 EngineAdapter
        ├── CHAT → (直接 ChatModel)
        ├── GRAPH → GraphEngineAdapter      # StateGraph 编排
        ├── AGENTSCOPE → AgentScopeAdapter  # Pipeline 编排
        └── HYBRID → HybridEngineAdapter    # 外层 Graph + 内层委托
    → ContextStoreFactory.getOrCreate() # 创建/复用会话上下文
    → adapter.execute(def, input, ctx)  # 委托执行
    → AgentMessage → ChatResponse       # 结果转换
```

#### Agent CRUD API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/agents` | 查询所有 Agent 定义 |
| POST | `/api/v1/agents` | 创建 Agent 定义 |
| GET | `/api/v1/agents/{agentId}` | 查询单个 Agent 定义 |
| PUT | `/api/v1/agents/{agentId}` | 更新 Agent 定义 |
| DELETE | `/api/v1/agents/{agentId}` | 删除 Agent 定义 |

---

### 5.3 知识库与 RAG 系统

#### 知识库 CRUD API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/knowledge/bases` | 创建知识库（上传文件 + 自动解析切分） |
| GET | `/api/v1/knowledge/bases` | 查询所有知识库 |
| DELETE | `/api/v1/knowledge/bases/{id}` | 删除知识库（级联删除分块与向量） |
| GET | `/api/v1/knowledge/bases/{id}/documents` | 查询知识库下的文档分块 |
| POST | `/api/v1/knowledge/bases/{id}/documents` | 向已有知识库追加文档 |

#### RAG 管线工作流程

```
用户查询 (query)
    │
    ▼
┌──────────────────────┐
│ 阶段1: 查询改写      │  LLM 将非正式查询转为结构化搜索词
└──────────┬───────────┘  失败时降级使用原始查询
           ▼
┌──────────────────────┐
│ 阶段2: 混合检索       │  向量搜索 (HNSW/COSINE) + BM25 全文检索
│        + RRF 融合重排 │  score = α/(K+rank_vec) + β/(K+rank_bm25)
└──────────┬───────────┘  α=0.7, β=0.3, K=60
           ▼
┌──────────────────────┐
│ 阶段3: 后检索优化     │  3.1 Rerank 精排（qwen3-rerank）→ 失败降级
│                      │  3.2 MMR 多样性重排（λ=0.7）
└──────────┬───────────┘
           ▼
┌──────────────────────┐
│ 阶段4: 构建增强提示词 │  将检索分块注入上下文 + 用户问题
└──────────────────────┘
```

#### 文件上传处理流程

```
MultipartFile
    → TikaDocumentReader 解析（PDF/Word/TXT 等）
    → TokenTextSplitter 分块（默认配置）
    → EmbeddingService 向量化（text-embedding-v3, 1024维）
    → VectorStore.add() 存储至 pgvector
    → DocumentChunkRepository 持久化元数据
    → KnowledgeBase 更新 docCount
```

#### 文件约束

- 最大文件大小：50MB（`spring.servlet.multipart.max-file-size`）
- 支持格式：PDF、Microsoft Office（Word/Excel/PPT）、TXT、HTML 等（由 Apache Tika 自动识别）

---

### 5.4 MCP Server 配置管理

#### API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/mcp` | 查询所有 MCP 配置 |
| POST | `/api/v1/mcp` | 创建 MCP 配置 |
| PUT | `/api/v1/mcp/{id}` | 更新 MCP 配置 |
| DELETE | `/api/v1/mcp/{id}` | 删除 MCP 配置 |

#### 传输协议

| 协议 | 字段 | 说明 |
|------|------|------|
| stdio | command + args | 本地进程通信，适合本地工具 |
| sse | url + headers | Server-Sent Events，适合远程 HTTP 服务 |
| streamableHttp | url + headers | 流式 HTTP，适合高吞吐场景 |

---

### 5.5 会话管理

#### API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/chat/sessions` | 查询会话列表（支持按 agentId 过滤） |
| GET | `/api/v1/chat/sessions/{sessionId}/messages` | 查询会话消息历史 |
| DELETE | `/api/v1/chat/sessions/{sessionId}` | 删除会话及关联数据 |

---

