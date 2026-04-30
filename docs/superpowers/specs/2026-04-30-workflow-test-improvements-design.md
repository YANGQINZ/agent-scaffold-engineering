# 工作流测试与交互改进设计

日期：2026-04-30

## 概述

三个改进需求：
1. 画布未保存时支持内联画布数据传递，后端动态构建 AgentDefinition 执行
2. 将"测试运行"功能合并到 ChatPanel，统一测试入口
3. 从节点输出手柄拖拽到画布空白区域时自动添加 ChatNode

## 需求 1：内联画布数据传递

### 问题

当前 `TaskRuntime.resolveAgentDefinition()` 仅从 `AgentRegistry` 查找 AgentDefinition。未保存的画布没有 `agentId`，测试运行时报 `AGENT_NOT_FOUND` 错误。

### 后端改动

#### ChatRequestDTO 新增字段

```java
/** 内联 Agent 定义（未保存画布时使用） */
private AgentDefinitionDTO agentDefinition;
```

#### ChatRequest 新增字段

```java
/** 内联 Agent 定义 */
private AgentDefinition agentDefinition;
```

#### ChatController.convertRequest() 增加映射

```java
.agentDefinition(dto.getAgentDefinition() != null
    ? AgentDefinitionConverter.toDomain(dto.getAgentDefinition()) : null)
```

`AgentController` 中已有完整的 `convertToDomain()` 私有方法（DTO → Domain 转换，按 engine 分发到四个子类）。将其提取为 trigger 模块的公共工具类 `AgentDefinitionConverter`，供 `ChatController` 和 `AgentController` 共用。

#### TaskRuntime.resolveAgentDefinition() 三级回退

```
1. agentId 非空 → agentRegistry.get(agentId)   // 已有逻辑
2. request.agentDefinition 非空 → 校验后直接返回  // 新增
3. 都没有 → 抛出 AgentException(AGENT_NOT_FOUND, "未提供 agentId 且未传递画布定义数据")
```

内联定义不注册到 AgentRegistry，是请求级别的临时对象。

#### 内联定义校验

对 GRAPH 类型：必须包含非空 `graphNodes` 和非空 `graphStart`。
对 AGENTSCOPE 类型：必须包含 `agentscopeAgents`。
对 HYBRID 类型：必须包含 `graphNodes` 和 `graphStart`。
对 CHAT 类型：必须包含 `instruction`。

校验失败返回具体字段缺失信息，如 "GRAPH 类型 Agent 定义缺少 graphStart 字段"。

### 前端改动

#### ChatRequest 类型新增

```typescript
/** 内联 Agent 定义（未保存画布时使用） */
agentDefinition?: AgentDefinition;
```

#### ChatPanel.handleSend（工作区页面）

当画布有节点时：
- `agentDefinition: currentAgentId ? undefined : exportToAgentDefinition()`
- 已保存的 Agent 不传定义（从 registry 查找）；未保存的内嵌画布数据
- 如果内嵌定义缺少 `agentId`，生成临时 ID：`temp_${timestamp}`

#### CanvasToolbar

移除"测试运行"按钮及 `handleTest` 函数、`testLoading` 状态。

## 需求 2：合并测试运行到 ChatPanel

### 工作区页面 (/) ChatPanel

#### handleSend 逻辑

- 读取 `useCanvasStore` 的 `currentAgentId`、`nodes`、`currentEngineType`
- 有画布节点（`nodes.length > 0`）：
  - `mode: 'AGENT'`
  - `agentId: currentAgentId || undefined`
  - `engine: currentEngineType || 'GRAPH'`
  - `agentDefinition: currentAgentId ? undefined : exportToAgentDefinition()`
- 无画布节点：
  - `mode: 'MULTI_TURN'`（退化为普通对话）

#### Header 显示

工作区页面的 ChatPanel Header 额外显示画布状态：
- 有 `currentAgentId` → 显示 agent 名称 + 引擎 badge（已有）
- 无 `currentAgentId` 但有节点 → 显示"未保存画布"标识 + 引擎 badge

#### 画布节点状态同步

当 ChatPanel 收到 `NODE_START`/`NODE_END` SSE 事件时，同时更新 `canvasStore.nodeStates`，让画布上的节点有视觉反馈（脉冲、变色）。

这需要在 `useSSE` hook 或 ChatPanel 中增加对 `canvasStore.setNodeState` 的调用。

#### CanvasToolbar 变更

- 移除"测试运行"按钮
- 移除 `handleTest` 函数
- 移除 `testLoading` 状态
- 保留：添加节点（专家模式）、保存、导出 YAML（专家模式）

### /chat 页面增强

#### ChatPanel.handleSend（/chat 页面）

- 已选 Agent（`selectedAgentId` 非空）→ `mode: 'AGENT'`，`agentId: selectedAgentId`
- 未选 Agent → `mode: 'MULTI_TURN'`
- /chat 页面不传 `agentDefinition`（Agent 都是从列表选的已保存 Agent）

#### NodeExecutionStatus 可见性

在 /chat 页面也显示 NodeExecutionStatus 组件：当 `selectedAgentId` 对应的 Agent 引擎为 GRAPH/HYBRID/AGENTSCOPE 时可见，展示节点执行进度胶囊条。

## 需求 3：拖拽到空白自动添加节点

### 交互流程

1. 用户从节点的 source handle 开始拖拽连线
2. 拖拽到另一个节点的 target handle → 正常连接（现有行为不变）
3. 拖拽到画布空白区域释放 → 自动创建新 ChatNode + 自动建立连线

### 实现

#### AgentCanvas.tsx 新增 onConnectStart

```typescript
const connectingFrom = useRef<{ nodeId: string | null; handleType: string | null }>({ nodeId: null, handleType: null });

onConnectStart={(event, { nodeId, handleType }) => {
  connectingFrom.current = { nodeId, handleType };
}}
```

仅当 `handleType === 'source'` 时记录。

#### AgentCanvas.tsx 新增 onConnectEnd

```typescript
onConnectEnd={(event) => {
  const { nodeId: sourceNodeId, handleType } = connectingFrom.current;

  // 仅处理从 source handle 拖出
  if (handleType !== 'source' || !sourceNodeId) {
    connectingFrom.current = { nodeId: null, handleType: null };
    return;
  }

  // 检查是否已成功连接（通过比较 edges 数量）
  // 如果有新 edge 产生，说明连到了有效 target，无需处理
  // 否则在鼠标释放位置创建新节点

  const reactFlowEvent = event as MouseEvent | TouchEvent;
  const screenPoint = 'clientX' in reactFlowEvent
    ? { x: reactFlowEvent.clientX, y: reactFlowEvent.clientY }
    : { x: 0, y: 0 };

  const flowPosition = screenToFlowPosition(screenPoint);

  const newNodeId = `node_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const newNode = {
    id: newNodeId,
    type: 'chat',
    position: flowPosition,
    data: { label: '对话节点', engine: 'CHAT' },
  };

  const newEdge = {
    id: `e_${sourceNodeId}-${newNodeId}`,
    source: sourceNodeId,
    target: newNodeId,
    type: 'default',
  };

  setNodes([...nodes, newNode]);
  setEdges([...edges, newEdge]);

  connectingFrom.current = { nodeId: null, handleType: null };
}}
```

#### 关键细节

- 使用 ReactFlow 实例的 `screenToFlowPosition()` 方法将鼠标屏幕坐标转为画布坐标
- 新节点默认为 ChatNode 类型，用户后续在 NodeEditPanel 中修改配置
- 新 edge 无 condition，type 为 `'default'`
- 需要通过 `useReactFlow()` hook 获取 `screenToFlowPosition` 方法

#### 边界情况

| 源节点类型 | 行为 |
|-----------|------|
| StartNode | 添加 ChatNode |
| ChatNode | 添加 ChatNode |
| EngineNode | 添加 ChatNode |
| target handle 拖出 | 忽略，不处理 |
| 画布为空 | 无 source handle，无法触发 |

#### 连接成功检测

需要在 `onConnectEnd` 中判断本次拖拽是否已产生了有效连接。方法：在 `onConnectStart` 时记录当前 edges 数量，在 `onConnectEnd` 时比较 — 如果 edges 增加了，说明已成功连接到有效 target，跳过自动添加逻辑。

## 影响范围

### 后端文件

| 文件 | 改动 |
|------|------|
| `ChatRequestDTO.java` | 新增 `agentDefinition` 字段 |
| `ChatRequest.java` | 新增 `agentDefinition` 字段 |
| `ChatController.java` | `convertRequest()` 增加映射 |
| `TaskRuntime.java` | `resolveAgentDefinition()` 增加第三条路径 |
| 新增 `AgentDefinitionConverter.java` | 从 AgentController 提取 DTO → Domain 转换器 |
| `AgentController.java` | 改为调用 AgentDefinitionConverter |

### 前端文件

| 文件 | 改动 |
|------|------|
| `ChatPanel.tsx` | handleSend 逻辑重写，增加画布数据传递 |
| `CanvasToolbar.tsx` | 移除测试运行按钮和相关逻辑 |
| `AgentCanvas.tsx` | 新增 `onConnectStart`/`onConnectEnd` |
| `useSSE.ts` | 增加 `canvasStore.nodeStates` 同步 |
| `api/chat.ts` | ChatRequest 类型新增 `agentDefinition` |
| `ChatPage.tsx` 或 `NodeExecutionStatus.tsx` | /chat 页面显示节点执行状态 |
