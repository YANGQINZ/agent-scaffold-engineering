# Testing Standard

> 最后更新：2026-04-29

## 测试框架

- 单元测试：JUnit 5（Spring Boot Starter Test）
- 集成测试：Spring Boot Test + MyBatis Test
- 端到端测试：暂无（建议后续引入）

## 测试目录结构

```
agent-scaffold-engineering-app/src/test/java/com/ai/agent/
├── domain/
│   ├── agent/service/         ← Agent 领域服务测试
│   └── knowledge/service/     ← Knowledge 领域服务测试
├── infrastructure/            ← 基础设施层测试
├── trigger/http/              ← Controller 测试
├── types/exception/           ← 异常类测试
└── test/                      ← 集成测试基类
```

## 命名规范

- 测试文件：`*Test.java`
- 测试方法：中文或英文描述性命名均可，表达清晰即可
- 示例：`AgentRegistryTest`、`ChatIntegrationTest`

## 覆盖率要求

| 模块 | 目标覆盖率 | 当前状态 |
|------|-----------|---------|
| domain 层（策略/路由/执行器） | 80% | 待补充 |
| infrastructure 层（Repository） | 60% | 待补充 |
| trigger 层（Controller） | 50% | 部分覆盖 |

## 冒烟测试

- 入口命令：`mvn test -pl agent-scaffold-engineering-app -DskipTests=false`
- 覆盖范围：
  - REST API 端点（/api/v1/chat、/api/v1/knowledge/*）
  - Agent 执行器注册和分发
  - RAG 流水线核心路径
- 执行时机：每次 merge 到主干前

## 回归测试

- 参考 `docs/05-TEST-QA/Regression-SSoT.md`
- 按 Cadence Ledger 规则触发

## 测试编写原则

1. 每个测试只验证一件事
2. 测试之间互相独立，不依赖执行顺序
3. 使用 fixture / factory 管理测试数据，不硬编码
4. mock 外部依赖（DashScope API、数据库），不 mock 内部实现
5. 测试失败时的错误信息要有诊断价值
6. 当前 `skipTests=true`，提交前必须手动运行验证

## 当前注意

- 项目默认 `skipTests=true`（在根 pom.xml 中配置）
- 运行测试时必须显式传 `-DskipTests=false`
- 集成测试需要 PostgreSQL (pgvector) 运行在 localhost:5432
