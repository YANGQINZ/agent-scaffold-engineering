# Agent Scaffold Engineering

> 这是 agent 进入项目时看到的第一个文件。它是目录和宪章，不是百科全书。
> 具体规范按需加载，不要一次读完所有 reference。

## 项目概况

- **项目名**：agent-scaffold-engineering
- **技术栈**：Java 17 / Spring Boot 3.4.3 / Spring AI 1.1.4 / Spring AI Alibaba 1.1.2.0 / MyBatis 3.0.4 / PostgreSQL (pgvector) / Maven
- **仓库类型**：单仓 monorepo（6 个 Maven 子模块）
- **主要模块**：
  - `agent-scaffold-engineering-app` — Bootstrap、Spring 配置、测试入口
  - `agent-scaffold-engineering-trigger` — HTTP 控制器
  - `agent-scaffold-engineering-domain` — 核心领域逻辑（无基础设施依赖）
  - `agent-scaffold-engineering-infrastructure` — Repository 实现、MyBatis Mapper、PO 类
  - `agent-scaffold-engineering-api` — 服务接口 + DTO
  - `agent-scaffold-engineering-types` — 枚举、常量、异常、Response<T>

## 硬规则

以下规则绝对不能违反：

1. **领域层零基础设施依赖**：`domain` 模块不得依赖 Spring、MyBatis、Redis 等任何基础设施框架。所有外部依赖通过 domain 层的 repository 接口 + infrastructure 层的实现来隔离。
2. **限界上下文边界**：domain 下有三个限界上下文（chat / agent / knowledge），跨上下文调用必须通过 Facade 或 Service 接口，不得直接引用另一个上下文的内部类。
3. **策略模式路由**：新增 ChatMode 或 AgentMode 时，必须实现对应的 Strategy 接口并注册到 Router/EnumMap，不允许 if-else 硬编码路由。
4. **不硬编码密钥**：API Key、数据库密码等敏感信息必须通过环境变量或配置文件注入，不允许出现在代码中。
5. **非平凡任务先建 task plan**：任何涉及 2 个以上文件改动的任务，先在 `docs/09-PLANNING/TASKS/` 下建任务目录和 task_plan.md。
6. **中文注释和日志**：代码注释、日志消息、异常消息使用中文。
7. **Lombok 全量使用**：使用 `@Data`、`@Builder`、`@Slf4j`、`@RequiredArgsConstructor` 等 Lombok 注解。

## Task-Type Reading Matrix

做什么类型的任务，先读哪个文件：

| 任务类型 | 先读 |
|----------|------|
| 架构 / DDD 分层 / 模块改动 | `docs/11-REFERENCE/engineering-standard.md` |
| chat 限界上下文（ChatStrategy / ModeRouter） | `docs/11-REFERENCE/engineering-standard.md` |
| agent 限界上下文（AgentRegistry / Executor） | `docs/11-REFERENCE/engineering-standard.md` |
| knowledge 限界上下文（RAG 流水线） | `docs/11-REFERENCE/engineering-standard.md` |
| REST API 新增或修改 | `docs/11-REFERENCE/engineering-standard.md` |
| 测试 / 冒烟 / 回归 | `docs/11-REFERENCE/testing-standard.md` |
| 开发执行 / commit / PR | `docs/11-REFERENCE/execution-workflow-standard.md` |
| 长程任务 / 连续执行 / 子代理审查 | `docs/11-REFERENCE/long-running-task-standard.md` |
| 文档治理 / planning | `docs/11-REFERENCE/docs-library-standard.md` |
| 回归 SSoT 维护 | `docs/11-REFERENCE/regression-ssot-governance.md` |
| Walkthrough 收口 | `docs/11-REFERENCE/walkthrough-standard.md` |
| Worktree 操作 | `docs/11-REFERENCE/worktree-standard.md` |
| 第三方集成（DashScope / MCP / pgvector） | `docs/06-INTEGRATIONS/` |

## 开发流程

1. 非平凡任务先建 task plan（`docs/09-PLANNING/TASKS/<任务名>/task_plan.md`）
2. 长程任务（预计 > 30 分钟连续执行）先补 `long-running-task-contract.md`
3. 回写 Feature SSoT（`docs/09-PLANNING/Feature-SSoT.md`）
4. 开 worktree，分支隔离（命名规范见 `docs/11-REFERENCE/worktree-standard.md`）
5. 编码 → 测试 → 本地验证
6. 完成后 merge + 跑回归（按 `docs/05-TEST-QA/Cadence-Ledger.md`）
7. 写 walkthrough（`docs/10-WALKTHROUGH/`）
8. 清理 worktree

## SSoT 文件

- **Feature SSoT**：`docs/09-PLANNING/Feature-SSoT.md`
- **Regression SSoT**：`docs/05-TEST-QA/Regression-SSoT.md`
- **Cadence Ledger**：`docs/05-TEST-QA/Cadence-Ledger.md`

## 协作规则

- 单人 + agent 开发模式，无需多 agent 并行协议
- Worktree 用于隔离 feature 开发，避免在主分支上做实验性改动
- 所有非平凡改动完成后必须写 walkthrough 收口记录
- 测试默认 skipTests=true，但新写的测试必须在提交前手动验证通过

## 构建与运行速查

```bash
# 完整构建（默认跳过测试）
mvn clean install

# 运行单个测试
mvn test -pl agent-scaffold-engineering-app -Dtest=ApiTest#test -DskipTests=false

# 运行应用（需要 AI_DASHSCOPE_API_KEY）
AI_DASHSCOPE_API_KEY=your-key mvn spring-boot:run -pl agent-scaffold-engineering-app
```

- 应用端口：8091，激活 profile：dev
- PostgreSQL (pgvector)：localhost:5432/agent_db
- Docker Compose：`docs/dev-ops/environment/docker-compose.yml`
