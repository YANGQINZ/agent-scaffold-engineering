# Cadence Ledger - Agent Scaffold Engineering

> 定义"什么情况下自动触发哪些回归面"。
> 新增 regression gate 时必须同步更新 trigger rules。

## Trigger Rules

| 改动范围 | 触发的 Regression Gates | 说明 |
|----------|----------------------|------|
| trigger 层（Controller/路由） | RG-001, RG-005 | Chat API + Knowledge Controller |
| domain/chat（ChatStrategy/ModeRouter） | RG-001 | Chat 集成测试 |
| domain/agent（AgentRegistry/Executor） | RG-002, RG-003 | Agent 注册 + 执行 |
| domain/knowledge（RAG 流水线） | RG-004 | MMR / Embedding |
| infrastructure 层（Repository/Mapper） | RG-001, RG-002, RG-004 | 持久化影响多个面 |
| domain/types（异常/常量/枚举） | Full Shared Batch | 基础类型变更影响全局 |
| application*.yml（配置文件） | Full Shared Batch | 配置变更影响全局 |
| pom.xml（依赖变更） | Full Shared Batch | 依赖变更影响全局 |
| 任何 merge 到 master | Full Shared Batch | 完整回归 |

## Shared Regression Batch Log

| Batch | Date | Scope | Trigger | Result | Notes | Next Checkpoint |
|-------|------|-------|---------|--------|-------|-----------------|
| SRB-001 | 2026-04-29 | Full | Harness bootstrap | 待执行 | 初始化回归体系，尚未执行首轮 batch | SRB-002 after 首个 feature 完成 |
