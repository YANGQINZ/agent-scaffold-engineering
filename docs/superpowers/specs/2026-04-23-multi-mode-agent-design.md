# 多模式AI Agent对话系统 — 设计文档

**日期**: 2026-04-23
**状态**: 已确认

---

## 1. 项目概述

在现有DDD脚手架基础上，设计并实现一个支持多种对话模式的AI Agent系统。系统采用策略模式路由架构，RAG作为可选增强层，支持YAML配置式Agent定义。

## 2. 对话模式定义

### 2.1 三种核心对话模式

| 模式 | 枚举值 | 说明 |
|------|--------|------|
| 单轮对话 | `SIMPLE` | 简单问答，无上下文记忆 |
| 多轮对话 | `MULTI_TURN` | 带上下文的连续对话，支持记忆管理 |
| 多智能体协同 | `AGENT` | 多Agent协作完成复杂任务，内含4种子模式 |

### 2.2 多智能体协同的4种子模式

| 子模式 | 枚举值 | 技术栈 | 适用场景 |
|--------|--------|--------|----------|
| Workflow编排 | `WORKFLOW` | Spring AI Alibaba Workflow | 有明确步骤顺序的任务 |
| ReAct Agent | `REACT` | AgentScope ReactAgent | 单Agent+工具调用场景 |
| Graph编排 | `GRAPH` | Spring AI Alibaba Graph | 需要条件路由的复杂流程 |
| Hybrid混合 | `HYBRID` | 外层Spring AI + 内层AgentScope | 外层流程控制+内层深度推理 |

### 2.3 RAG增强层

RAG不是独立的第四种模式，而是**可选增强层**，可叠加到任意对话模式上。用户通过 `ragEnabled=true` 参数启用。

RAG知识库来源支持：
- 管理员预置基础库
- 用户上传补充文档（PDF/Word/TXT）

## 3. 整体架构

### 3.1 架构方案：策略模式路由（Strategy Router）

```
┌─────────────────────────────────────────────────┐
│  trigger (HTTP)                                  │
│  ChatController → 统一入口 /chat + /chatStream   │
└──────────────────────┬──────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────┐
│  domain                                          │
│  ┌─────────────────────────────────────────┐     │
│  │ ChatFacade (门面)                        │     │
│  │  → ModeRouter (模式路由)                 │     │
│  │  → RagDecorator (RAG装饰器, 可选)        │     │
│  └──────────┬──────────────────────────────┘     │
│             ▼                                     │
│  ┌──────────┬──────────┬──────────────────┐      │
│  │SimpleChat│MultiTurn │AgentOrchestrator │      │
│  │Strategy  │Strategy  │Strategy          │      │
│  │          │+记忆管理  │→ 4种子模式       │      │
│  └──────────┴──────────┴──────────────────┘      │
│                                                   │
│  Agent子模式: Workflow / ReAct / Graph / Hybrid   │
│  AgentRegistry (YAML配置加载)                      │
│  ChatMemoryManager (对话记忆管理)                   │
└──────────────────────┬──────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────┐
│  infrastructure                                  │
│  PostgreSQL + PGvector (向量存储/对话持久化)       │
│  AgentConfigRepository (Agent YAML加载)           │
│  ChatMemoryRepository (对话记忆持久化)             │
│  DocumentRepository (文档/切片存储)                │
└─────────────────────────────────────────────────┘
```

### 3.2 核心组件职责

- **ChatFacade**：domain层门面服务，接收请求、调用ModeRouter获取Strategy，若ragEnabled=true则用RagDecorator包裹Strategy
- **ModeRouter**：根据 `mode` 参数路由到对应Strategy并返回
- **RagDecorator**：装饰器模式，由ChatFacade在ragEnabled=true时包裹目标Strategy
- **AgentOrchestratorStrategy**：内部再根据 `agentMode` 路由到4个子编排器
- **AgentRegistry**：启动时加载YAML中的Agent定义，运行时提供Agent实例

## 4. 领域模型设计

基于现有DDD脚手架，新增3个限界上下文，替换原有的xxx/yyy模板上下文。

### 4.1 chat 限界上下文

- **聚合根**: `ChatSession` — sessionId, userId, mode, agentMode, ragEnabled
- **实体**: `ChatMessage` — messageId, role, content, timestamp
- **值对象**: `ChatRequest` — query, mode, agentMode, ragEnabled, knowledgeBaseId
- **值对象**: `ChatResponse` — answer, sources(引用来源), metadata
- **领域服务**: `ChatFacade`(门面)、`ModeRouter`(路由)
- **策略**: `SimpleChatStrategy`、`MultiTurnStrategy`、`AgentOrchestratorStrategy`
- **仓库接口**: `ChatSessionRepository`

### 4.2 agent 限界上下文

- **聚合根**: `AgentDefinition` — agentId, name, instruction, mode, tools, modelConfig
- **实体**: `WorkflowDefinition` — nodes[], edges[], entryNode
- **实体**: `ToolConfig` — toolName, toolType, parameters
- **值对象**: `ModelConfig` — modelName, temperature, maxTokens
- **领域服务**: `AgentOrchestrator`(4种子编排调度)、`AgentRegistry`(YAML配置注册)
- **子编排器**: `WorkflowExecutor`、`ReactExecutor`、`GraphExecutor`、`HybridExecutor`
- **仓库接口**: `AgentDefinitionRepository`

### 4.3 knowledge 限界上下文

- **聚合根**: `KnowledgeBase` — baseId, name, description, ownerType, ownerId
- **实体**: `Document` — docId, fileName, fileType, status, chunkCount
- **实体**: `DocumentChunk` — chunkId, content, embedding, metadata
- **领域服务**: `RagService`(检索增强)、`DocumentProcessor`(文档切片)、`EmbeddingService`(向量化)
- **仓库接口**: `KnowledgeBaseRepository`、`DocumentRepository`、`DocumentChunkRepository`

## 5. 请求处理流程

### 5.1 API入口

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/chat` | POST | 同步对话，返回完整响应 |
| `/api/v1/chat/stream` | POST | 流式对话，返回 `Flux<ChatResponse>` |
| `/api/v1/knowledge/upload` | POST | 上传文档到知识库 |

### 5.2 请求体 ChatRequest

```java
public class ChatRequest {
    private String userId;            // 用户标识
    private String sessionId;         // 会话ID（多轮必传）
    private String query;             // 用户输入
    private ChatMode mode;            // SIMPLE / MULTI_TURN / AGENT
    private AgentMode agentMode;      // WORKFLOW / REACT / GRAPH / HYBRID（mode=AGENT时必传）
    private Boolean ragEnabled;       // 是否启用RAG增强
    private String knowledgeBaseId;   // RAG知识库ID（ragEnabled=true时必传）
}
```

### 5.3 五条核心处理链路

**1. 单轮对话** (mode=SIMPLE, ragEnabled=false):
```
ChatController → ChatFacade → ModeRouter → SimpleChatStrategy → ChatModel.call() → ChatResponse
```

**2. 多轮对话** (mode=MULTI_TURN, ragEnabled=false):
```
ChatController → ChatFacade → ModeRouter → MultiTurnStrategy → 加载记忆 → ChatModel.call() → 存储记忆 → ChatResponse
```

**3. 多智能体协同** (mode=AGENT, agentMode=WORKFLOW/REACT/GRAPH/HYBRID):
```
ChatController → ChatFacade → ModeRouter → AgentOrchestratorStrategy → [WorkflowExecutor | ReactExecutor | GraphExecutor | HybridExecutor] → ChatResponse
```

**4. RAG装饰器叠加** (ragEnabled=true, 任意mode):
```
ChatFacade → ModeRouter获取Strategy → RagDecorator包裹Strategy → 进阶RAG三段式检索增强 → delegate.execute(增强请求)
```

**5. 流式响应** (所有模式均支持):
```
ChatController.chatStream() → ChatFacade.chatStream() → ChatModel.stream() / Agent.stream() → Flux<StreamEvent>
```

### 5.4 SSE流式事件类型

chatStream端点返回 `Flux<StreamEvent>`，通过SSE推送给前端。不同模式下推送的事件类型不同：

| 事件类型 | 枚举值 | 适用模式 | 说明 |
|----------|--------|----------|------|
| 文本片段 | `TEXT_DELTA` | 所有模式 | LLM生成的文本片段，逐token推送 |
| 思考过程 | `THINKING` | AGENT | Agent推理/思考步骤（ReAct的Observation/Thought/Action） |
| 节点执行 | `NODE_START` / `NODE_END` | AGENT(Workflow/Graph/Hybrid) | 工作流/图节点开始/完成，含节点名称和状态 |
| RAG检索 | `RAG_RETRIEVE` | ragEnabled=true | 检索到的文档片段和来源，前端可展示引用 |
| 完成 | `DONE` | 所有模式 | 流式结束标记，包含完整metadata |

**SSE事件格式**:
```json
{"type": "TEXT_DELTA", "data": {"content": "根据"}, "sessionId": "xxx"}
{"type": "THINKING", "data": {"step": "Observation", "content": "正在查询天气数据..."}, "sessionId": "xxx"}
{"type": "NODE_START", "data": {"nodeId": "collect_info", "nodeName": "信息收集"}, "sessionId": "xxx"}
{"type": "NODE_END", "data": {"nodeId": "collect_info", "nodeName": "信息收集", "output": "..."}, "sessionId": "xxx"}
{"type": "RAG_RETRIEVE", "data": {"sources": [{"docName": "xxx", "chunkContent": "...", "score": 0.95}]}, "sessionId": "xxx"}
{"type": "DONE", "data": {"ragDegraded": false, "metadata": {}}, "sessionId": "xxx"}
```

**设计要点**：
- 前端可根据 `type` 区分展示：TEXT_DELTA拼接到回答区，THINKING展示在思考过程面板，NODE_START/END展示在进度时间线
- RAG模式下检索到的sources通过 `RAG_RETRIEVE` 事件推送，前端可实时展示引用来源
- 单轮/多轮模式下仅推送 `TEXT_DELTA` + `DONE`，行为与普通流式一致

## 6. Agent YAML配置与4种编排子模式

### 6.1 YAML配置规范

配置文件位于 `app/src/main/resources/agents/` 目录，每个YAML文件定义一组Agent。

**通用字段**: `id`(唯一标识)、`name`(显示名称)、`mode`(编排模式)、`instruction`(系统提示词)、`model`(模型配置)、`tools`(工具列表)

**工具定义**: FUNCTION类型指定className，MCP类型自动发现。

### 6.2 四种编排子模式

**REACT模式** — AgentScope ReactAgent:
- 单Agent推理-行动循环：观察→思考→行动→观察...
- 执行器：`ReactExecutor` → 直接创建 `ReactAgent` 实例
- YAML示例：
```yaml
agents:
  - id: "weather-assistant"
    name: "天气助手"
    mode: REACT
    instruction: "你是一个天气查询助手..."
    model: { name: "qwq-plus", temperature: 0.7, maxTokens: 2000 }
    tools:
      - name: "getWeather"
        type: FUNCTION
        description: "查询城市天气"
        className: "com.ai.agent.tools.WeatherTool"
```

**WORKFLOW模式** — Spring AI Alibaba Workflow:
- 线性节点链，前节点输出作为后节点输入
- YAML中定义 `workflow.entry` 和 `nodes[].next`
- 执行器：`WorkflowExecutor` → 按node顺序依次调用Agent
- YAML示例：
```yaml
  - id: "travel-planner"
    name: "旅行规划师"
    mode: WORKFLOW
    instruction: "帮用户规划旅行行程"
    model: { name: "qwq-plus", temperature: 0.5 }
    workflow:
      entry: "collect_info"
      nodes:
        - id: "collect_info"
          agentId: "info-collector"
          next: "search_routes"
        - id: "search_routes"
          agentId: "route-searcher"
          next: "generate_plan"
        - id: "generate_plan"
          agentId: "plan-generator"
```

**GRAPH模式** — Spring AI Alibaba Graph:
- 图结构编排，支持分支和循环
- YAML中定义 `graph.start`、`nodes[]`、`edges[].condition`
- 执行器：`GraphExecutor` → 构建StateGraph执行
- YAML示例：
```yaml
  - id: "customer-service"
    name: "客服系统"
    mode: GRAPH
    instruction: "智能客服路由"
    model: { name: "qwq-plus" }
    graph:
      start: "classify"
      nodes:
        - id: "classify"
          agentId: "classifier"
        - id: "faq"
          agentId: "faq-bot"
        - id: "human"
          agentId: "human-agent"
      edges:
        - from: "classify" to: "faq"   condition: "type==faq"
        - from: "classify" to: "human" condition: "type==complex"
```

**HYBRID模式** — Spring AI外层 + AgentScope内层:
- 外层Workflow编排整体流程，节点中可嵌入ReAct Agent
- YAML中workflow节点的 `reactAgentId` 标识内层ReAct Agent
- 执行器：`HybridExecutor` → Workflow驱动，遇到reactAgentId时创建ReactAgent
- YAML示例：
```yaml
  - id: "research-hybrid"
    name: "研究助手"
    mode: HYBRID
    instruction: "深度研究分析"
    model: { name: "qwq-plus" }
    workflow:
      entry: "research"
      nodes:
        - id: "research"
          reactAgentId: "deep-researcher"  # 内层ReAct
          next: "summarize"
        - id: "summarize"
          agentId: "summarizer"
```

### 6.3 AgentRegistry加载机制

- 应用启动时扫描 `agents/` 目录下所有YAML
- 解析为 `AgentDefinition` 对象，注册到内存Map
- 运行时通过 `agentId` 查找定义，按需创建Agent实例

## 7. RAG系统设计

### 7.1 文档上传处理流程

```
上传文档 → DocumentProcessor → 解析(PDF/Word/TXT) → TokenTextSplitter切片 → DashScope Embedding向量化 → PGvector存储
```

- `DocumentProcessor`：根据文件类型选择解析器
- `TokenTextSplitter`：Spring AI提供的文本切片器
- `EmbeddingService`：调用DashScope Embedding API生成向量

### 7.2 进阶RAG三段式检索增强

**阶段1：预检索优化（Pre-retrieval）**
- 查询改写：用LLM将口语化提问改写为规范化检索词
- 示例：`"为啥报错"` → `"系统报错原因分析 故障排查 错误日志"`

**阶段2：混合检索融合（Hybrid Retrieval & Fusion）**
- 双路并行检索：
  - 向量检索（PGvector cosine）— 懂语义，容错率高
  - 关键词检索（BM25，PG全文索引）— 精确匹配度高
- 分数归一化 + 权重融合：`score = α·norm(vec_score) + (1-α)·norm(bm25_score)`

**阶段3：后检索优化（Post-retrieval）**
- Reranking重排序：对融合候选集精排（组件待定：阿里云百炼或其他，后续讨论）
- MMR多样性重排：减少冗余片段，保证覆盖面
- 输出最终 Top-K 片段

**完整管线**:
```
原始query → 查询改写 → [向量检索 + BM25检索] → 分数归一化融合 → Reranking → MMR多样性重排 → Top-K片段 → 增强Prompt
```

## 8. 对话记忆管理

### 8.1 内存+数据库混合架构

**热数据层（内存）**:
- 存储：`ConcurrentHashMap<sessionId, LinkedList<ChatMessage>>`
- 策略：每个session保留最近20轮对话
- 淘汰：LRU，超出容量时最旧记录写回DB并移除
- 读取：请求时优先从内存读取

**冷数据层（PostgreSQL）**:
- 表：`chat_session` + `chat_message`
- 写入：异步批量落库（每N条或每M秒flush）
- 读取：内存miss时从DB加载，回填内存
- 清理：超过TTL的session定期归档

### 8.2 记忆加载流程

```
MultiTurnStrategy.execute()
  → ChatMemoryManager.load(sessionId)
  → 内存命中? → 直接用 : → DB加载回填
  → 构建带历史Prompt
  → ChatModel.call()
  → ChatMemoryManager.save(新消息)
  → 写内存 + 异步落库
```

## 9. 数据库表结构

### 9.1 对话域

**chat_session**:
| 字段 | 类型 | 说明 |
|------|------|------|
| session_id | VARCHAR PK | 会话ID |
| user_id | VARCHAR | 用户标识 |
| mode | VARCHAR(20) | 对话模式 |
| agent_mode | VARCHAR(20) | Agent子模式（可为空） |
| rag_enabled | BOOLEAN | 是否启用RAG |
| knowledge_base_id | VARCHAR | 知识库ID（可为空） |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |
| ttl_expire_at | TIMESTAMP | TTL过期时间 |

**chat_message**:
| 字段 | 类型 | 说明 |
|------|------|------|
| message_id | BIGSERIAL PK | 消息ID |
| session_id | VARCHAR FK | 关联会话 |
| role | VARCHAR(20) | 角色（user/assistant/system） |
| content | TEXT | 消息内容 |
| token_count | INT | Token数量 |
| created_at | TIMESTAMP | 创建时间 |

索引：`session_id + created_at`

### 9.2 知识库域

**knowledge_base**:
| 字段 | 类型 | 说明 |
|------|------|------|
| base_id | VARCHAR PK | 知识库ID |
| name | VARCHAR | 名称 |
| description | TEXT | 描述 |
| owner_type | VARCHAR(20) | ADMIN/USER |
| owner_id | VARCHAR | 所属者ID |
| doc_count | INT | 文档数量 |
| created_at | TIMESTAMP | 创建时间 |

**document**:
| 字段 | 类型 | 说明 |
|------|------|------|
| doc_id | VARCHAR PK | 文档ID |
| base_id | VARCHAR FK | 关联知识库 |
| file_name | VARCHAR | 文件名 |
| file_type | VARCHAR(10) | 文件类型 |
| status | VARCHAR(20) | PENDING/PROCESSING/COMPLETED/FAILED |
| chunk_count | INT | 切片数量 |
| created_at | TIMESTAMP | 创建时间 |

**document_chunk**:
| 字段 | 类型 | 说明 |
|------|------|------|
| chunk_id | BIGSERIAL PK | 切片ID |
| doc_id | VARCHAR FK | 关联文档 |
| base_id | VARCHAR FK | 关联知识库 |
| content | TEXT | 切片内容 |
| embedding | vector(1536) | 向量（DashScope Embedding） |
| content_tsv | tsvector | 全文检索列（GENERATED） |
| metadata | JSONB | 元信息 |
| chunk_index | INT | 切片序号 |

索引：
- `base_id`, `doc_id`
- HNSW索引：`embedding vector_cosine_ops`
- GIN索引：`content_tsv`

### 9.3 BM25全文检索SQL

```sql
ALTER TABLE document_chunk ADD COLUMN content_tsv tsvector
  GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;

CREATE INDEX idx_chunk_content_tsv ON document_chunk
  USING GIN (content_tsv);

CREATE INDEX idx_chunk_embedding ON document_chunk
  USING hnsw (embedding vector_cosine_ops);
```

## 10. 错误处理与容错

### 10.1 异常分层体系

- **AppException**（已有）— 通用业务异常，携带code + info
- **ChatException** — 对话域异常：模式不支持、会话过期等
- **AgentException** — Agent域异常：编排失败、Agent未找到等
- **KnowledgeException** — 知识库异常：文档处理失败、检索超时等

所有异常继承 `AppException`，统一由trigger层全局异常处理器捕获，转为 `Response<T>` 格式。

### 10.2 关键故障场景 & 降级策略

| 故障场景 | 降级策略 |
|----------|----------|
| LLM调用超时/失败 | 返回友好提示"服务繁忙请稍后重试"，流式已发送部分则正常结束 |
| PGvector检索失败 | RAG装饰器捕获异常，跳过检索回退到无RAG执行，响应标记 `ragDegraded=true` |
| Agent编排节点失败 | 错误信息传递给后续节点，不可继续则返回已执行步骤结果 |
| 文档处理失败 | document.status=FAILED，不影响其他文档，用户可重新上传 |
| 对话记忆DB写入失败 | 仅记日志，内存数据仍在，不影响当前对话 |

### 10.3 统一响应格式

```json
{
  "code": "0000",
  "info": "success",
  "data": {
    "answer": "...",
    "sources": [],
    "sessionId": "...",
    "ragDegraded": false,
    "metadata": {}
  }
}
```

错误码规范：
- `CHAT_xxxx` — 对话域错误
- `AGENT_xxxx` — Agent域错误
- `KNOW_xxxx` — 知识库域错误

## 11. 技术选型总结

| 组件 | 选型 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.4.3 |
| Java | OpenJDK | 17 |
| Spring AI BOM | | 1.1.4 |
| Spring AI Alibaba BOM | | 1.1.2.0 |
| 默认模型 | qwq-plus (DashScope) | |
| 向量数据库 | PostgreSQL + PGvector | |
| Embedding | DashScope Embedding | |
| Agent框架(Workflow/Graph) | Spring AI Alibaba Agent Framework | |
| Agent框架(ReAct) | AgentScope Java | 1.0.11 |
| 文档切片 | Spring AI TokenTextSplitter | |
| ORM | MyBatis | |
| 用户体系 | 简单userId标识，无鉴权 | |
