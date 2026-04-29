# Engineering Standard

> 最后更新：2026-04-29

## 项目架构原则

1. **DDD 分层解耦**：严格遵循 app → trigger → domain → infrastructure → api → types 的依赖方向。domain 层零基础设施依赖。
2. **限界上下文隔离**：domain 下三个限界上下文（chat / agent / knowledge）通过 Facade/Service 接口交互，不得直接引用内部类。
3. **策略 + 路由模式**：ChatStrategy + ModeRouter、AgentOrchestratorStrategy + EnumMap。新增模式必须实现接口并注册，禁止 if-else 硬编码。
4. **装饰器模式**：RagDecorator 包装 ChatStrategy，按 ragEnabled 开关注入 RAG 能力。
5. **仓储接口下沉**：领域层定义接口（如 IChatSessionRepository），基础设施层实现。

## 代码风格

- 语言：Java 17
- Linter：无（建议后续引入 Checkstyle）
- 格式化：IDE 默认 Java 格式化
- 注释、日志、异常消息：中文
- 全量使用 Lombok（@Data、@Builder、@Slf4j、@RequiredArgsConstructor）

## 模块组织

```
agent-scaffold-engineering/
├── agent-scaffold-engineering-app/          ← Bootstrap、Spring 配置、测试
├── agent-scaffold-engineering-trigger/      ← HTTP 控制器
├── agent-scaffold-engineering-domain/       ← 核心领域逻辑
│   └── src/main/java/com/ai/agent/domain/
│       ├── chat/                            ← 聊天限界上下文
│       ├── agent/                           ← Agent 限界上下文
│       └── knowledge/                       ← 知识限界上下文
├── agent-scaffold-engineering-infrastructure/ ← Repository 实现、MyBatis、PO
├── agent-scaffold-engineering-api/          ← 服务接口 + DTO
└── agent-scaffold-engineering-types/        ← 枚举、常量、异常、Response<T>
```

## 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | PascalCase | `AgentRegistry`、`ChatFacade` |
| 方法名 | camelCase | `routeByMode()`、`executeAgent()` |
| 常量 | UPPER_SNAKE_CASE | `DEFAULT_ALPHA`、`MAX_RETRY` |
| 包名 | 全小写 | `com.ai.agent.domain.chat` |
| 配置文件 | kebab-case | `application-dev.yml` |
| MyBatis Mapper XML | PascalCase + Mapper | `ChatSessionMapper.xml` |

## 错误处理

- 使用 `Response<T>` 统一包装 API 响应
- 异常使用 `types` 模块中定义的异常类
- 不允许在 domain 层 catch 并吞掉异常

## 日志规范

- 使用 `@Slf4j` Lombok 注解
- 关键操作（策略路由、Agent 执行、RAG 流水线）记录 INFO 日志
- 异常记录 ERROR 日志，包含完整异常栈
- 日志消息使用中文

## 性能约束

- RAG 混合搜索参数：向量 α=0.7，BM25 β=0.3
- 流式响应使用 Flux（WebFlux SSE）
- Redis/MQ 配置已注释，启用时需注意连接池配置

## 安全约束

1. 不允许在代码中硬编码密钥或凭证
2. AI_DASHSCOPE_API_KEY 通过环境变量注入
3. 数据库连接信息通过 application-{profile}.yml 配置
