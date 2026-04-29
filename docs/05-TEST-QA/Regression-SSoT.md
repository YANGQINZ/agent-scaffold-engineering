# Regression SSoT - Agent Scaffold Engineering

> 单一事实源：管理所有 regression surface 的状态、证据深度和残项。
> 新增固定 gate 或 evidence depth 变化时必须更新。

## Active Fixed Gates

| ID | Status | Surface | Primary Entrypoint | Evidence Depth | Last Verified | Notes |
|----|--------|---------|-------------------|----------------|---------------|-------|
| RG-001 | 🟡 | REST API - Chat | `mvn test -pl agent-scaffold-engineering-app -Dtest=ChatIntegrationTest -DskipTests=false` | L2 (local_smoke) | 2026-04-29 | 需要数据库 |
| RG-002 | 🟡 | Agent 执行器 | `mvn test -pl agent-scaffold-engineering-app -Dtest=AgentRegistryTest -DskipTests=false` | L1 (tests) | 2026-04-29 | |
| RG-003 | 🟡 | Agent 执行器 - TaskRuntime | `mvn test -pl agent-scaffold-engineering-app -Dtest=TaskRuntimeTest -DskipTests=false` | L1 (tests) | 2026-04-29 | |
| RG-004 | 🟡 | RAG 流水线 - MMR | `mvn test -pl agent-scaffold-engineering-app -Dtest=MmrServiceTest -DskipTests=false` | L1 (tests) | 2026-04-29 | |
| RG-005 | 🟡 | Knowledge Controller | `mvn test -pl agent-scaffold-engineering-app -Dtest=KnowledgeControllerTest -DskipTests=false` | L1 (tests) | 2026-04-29 | |

## Evidence Depth Legend

| Level | Name | Description |
|-------|------|-------------|
| L1 | tests | 只有单元测试 |
| L2 | local_smoke | 本地冒烟测试通过 |
| L3 | live | 真实环境端到端验证 |
| L4 | browser_human_proxy | 浏览器模拟真人操作 |
| L5 | hard_gate | 结构化判定 + 非零退出 |

## Residual Items

| ID | Surface | Issue | Priority | Created |
|----|---------|-------|----------|---------|
| R-001 | 全局 | skipTests=true 默认跳过测试，需手动验证 | P2 | 2026-04-29 |
| R-002 | 全局 | 无 CI/CD，Evidence Depth 暂限于 L1-L2 | P2 | 2026-04-29 |

## Status Legend

- 🟢 通过
- 🟡 部分通过 / 有已知残项
- 🔴 失败
- ⏸ 暂停
