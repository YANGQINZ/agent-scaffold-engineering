# Long-Running Task Standard

> 最后更新：2026-04-29

## 核心思路

长程任务不是"让 agent 多跑一会儿"，而是把任务先设计成可连续执行、可审查、可停止的合同。

当任务需要持续数小时、多轮 hardening、多 agent 分工或子代理 review 时，必须先把合同写清楚，再开放连续执行权限。

## 适用场景

以下任务应使用本标准：

- 预计持续多轮迭代的复杂修复、重构、交付收口
- 需要连续推进 2 小时以上的任务
- 需要多轮测试、真实环境 smoke 或人工代理操作的任务
- 需要 reviewer agent、subagent、外部审查者交叉检查的任务
- 用户希望"不要每轮问我，直到满足停止条件再汇报"的任务

以下任务通常不需要：

- 单文件小修
- 一次性命令执行
- 纯只读分析
- 没有客观验收口径的轻量 brainstorming

## 四个前提

1. **开放执行权限**：agent 不需要每一轮都回来请求继续
2. **封闭验收口径**：什么算完成、什么不算完成，事先定义
3. **持续证据循环**：每一轮都能产生新的可验证证据
4. **明确停止条件**：agent 知道什么时候继续，什么时候停

## 任务合同

长程任务开始前，必须在 task plan 或独立 contract 文件中写清以下字段：

### Goal
- 只能有一个主目标
- 不能写成"整体优化一下"

### Scope
- 允许修改哪些目录、模块、接口、文档
- 明确 out of scope
- 标出共享文件和冲突风险

### Primary Caller / Entry Surface
- CLI / local agent / UI / API / automation / integration

### Execution Permission
- 是否允许连续执行
- 是否允许启动子代理或 reviewer
- 哪些动作仍需人工批准

### Review Loop
- implement → test → self-review → fix
- 如使用子代理，写清审查范围和输出格式

### Evidence
- lint / typecheck / build
- unit / integration / e2e tests
- local smoke
- reviewer findings

### Stop Condition
- 关键路径通过
- 目标 regression gate 通过
- runtime errors 清零或有明确残项
- reviewer 无 material finding

### Deliverables
- 代码改动、测试、docs 回写、walkthrough、residual risk summary

## 暂停条件

即使授权连续执行，出现以下情况必须停下来汇报：

- 主目标或 scope 变得不清楚
- 需要高风险产品、架构、安全或数据决策
- 与已有未提交改动直接冲突
- 外部环境阻塞

## 反模式

- "你先看看，能改多少改多少"
- "整体优化一下"
- "差不多就行"
- "不用测了，先做"

核心原则：**开放执行，封闭验收；多轮证据，不靠感觉。**
