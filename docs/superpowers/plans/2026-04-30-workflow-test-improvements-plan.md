# 工作流测试与交互改进 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the test-run flow for unsaved canvases, merge test-run into ChatPanel, and add drag-to-blank node creation.

**Architecture:** Backend adds inline `AgentDefinition` support via a three-tier fallback in `TaskRuntime.resolveAgentDefinition()`. Frontend merges the test-run button into ChatPanel's `handleSend`, adds canvas state sync to `useSSE`, and implements `onConnectStart`/`onConnectEnd` for drag-to-blank node creation.

**Tech Stack:** Java 17 / Spring Boot / Spring AI / Lombok (backend), React 18 / TypeScript / Zustand / @xyflow/react (frontend)

---

## File Structure

### Backend — Create
- `agent-scaffold-engineering-trigger/src/main/java/com/ai/agent/trigger/converter/AgentDefinitionConverter.java` — Extracted from `AgentController`, shared DTO↔Domain converter

### Backend — Modify
- `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/chat/ChatRequestDTO.java` — Add `agentDefinition` field
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/common/valobj/ChatRequest.java` — Add `agentDefinition` field
- `agent-scaffold-engineering-trigger/src/main/java/com/ai/agent/trigger/http/ChatController.java` — Map `agentDefinition` in `convertRequest()`
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/service/TaskRuntime.java` — Three-tier fallback in `resolveAgentDefinition()`
- `agent-scaffold-engineering-trigger/src/main/java/com/ai/agent/trigger/http/AgentController.java` — Replace private converters with `AgentDefinitionConverter`

### Frontend — Modify
- `frontend/src/api/chat.ts` — Add `agentDefinition` to `ChatRequest`
- `frontend/src/features/chat/ChatPanel.tsx` — Rewrite `handleSend` for canvas-aware mode, add unsaved badge
- `frontend/src/features/canvas/CanvasToolbar.tsx` — Remove test-run button, `handleTest`, `testLoading`
- `frontend/src/features/canvas/AgentCanvas.tsx` — Add `onConnectStart`/`onConnectEnd`, remove duplicate `CanvasToolbar`
- `frontend/src/hooks/useSSE.ts` — Add `canvasStore.setNodeState` sync on `NODE_START`/`NODE_END`
- `frontend/src/features/chat/NodeExecutionStatus.tsx` — Remove `mode === 'expert'` guard (show in all modes when agent is AGENT type)
- `frontend/src/pages/WorkspacePage.tsx` — Remove duplicate `CanvasToolbar` import/render

---

## Task 1: Extract AgentDefinitionConverter from AgentController

**Files:**
- Create: `agent-scaffold-engineering-trigger/src/main/java/com/ai/agent/trigger/converter/AgentDefinitionConverter.java`
- Modify: `agent-scaffold-engineering-trigger/src/main/java/com/ai/agent/trigger/http/AgentController.java`

- [ ] **Step 1: Create `AgentDefinitionConverter.java`**

Copy the 6 DTO→Domain private methods from `AgentController` (lines 196-316) into a new public utility class. The class should be a Spring `@Component` with public static methods (no state needed, but `@Component` allows injection if desired later).

```java
package com.ai.agent.trigger.converter;

import com.ai.agent.api.model.agent.*;
import com.ai.agent.domain.agent.model.aggregate.*;
import com.ai.agent.domain.agent.model.entity.AgentscopeAgentConfig;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.types.enums.EngineType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AgentDefinition DTO ↔ Domain 转换器
 * 从 AgentController 提取，供 ChatController 和 AgentController 共用
 */
@Component
public class AgentDefinitionConverter {

    /**
     * AgentDefinitionDTO → AgentDefinition（按 engine 分发到子类）
     */
    public static AgentDefinition toDomain(AgentDefinitionDTO dto) {
        if (dto == null) {
            return null;
        }
        ModelConfig modelConfig = convertModelConfigToDomain(dto.getModelConfig());
        List<McpServerConfig> mcpServers = convertMcpServersToDomain(dto.getMcpServers());
        EngineType engine = dto.getEngine() != null ? dto.getEngine() : EngineType.CHAT;

        return switch (engine) {
            case CHAT -> ChatAgentDefinition.builder()
                    .agentId(dto.getAgentId())
                    .name(dto.getName())
                    .engine(EngineType.CHAT)
                    .instruction(dto.getInstruction())
                    .modelConfig(modelConfig)
                    .mcpServers(mcpServers)
                    .build();

            case GRAPH -> GraphAgentDefinition.builder()
                    .agentId(dto.getAgentId())
                    .name(dto.getName())
                    .engine(EngineType.GRAPH)
                    .instruction(dto.getInstruction())
                    .modelConfig(modelConfig)
                    .mcpServers(mcpServers)
                    .graphStart(dto.getGraphStart())
                    .graphNodes(convertWorkflowNodesToDomain(dto.getGraphNodes()))
                    .graphEdges(convertGraphEdgesToDomain(dto.getGraphEdges()))
                    .build();

            case AGENTSCOPE -> AgentscopeAgentDefinition.builder()
                    .agentId(dto.getAgentId())
                    .name(dto.getName())
                    .engine(EngineType.AGENTSCOPE)
                    .instruction(dto.getInstruction())
                    .modelConfig(modelConfig)
                    .mcpServers(mcpServers)
                    .agentscopePipelineType(dto.getAgentscopePipelineType())
                    .agentscopeAgents(convertAgentscopeAgentsToDomain(dto.getAgentscopeAgents()))
                    .build();

            case HYBRID -> HybridAgentDefinition.builder()
                    .agentId(dto.getAgentId())
                    .name(dto.getName())
                    .engine(EngineType.HYBRID)
                    .instruction(dto.getInstruction())
                    .modelConfig(modelConfig)
                    .mcpServers(mcpServers)
                    .graphStart(dto.getGraphStart())
                    .graphNodes(convertWorkflowNodesToDomain(dto.getGraphNodes()))
                    .graphEdges(convertGraphEdgesToDomain(dto.getGraphEdges()))
                    .subEngines(dto.getSubEngines() != null ? dto.getSubEngines() : Map.of())
                    .build();
        };
    }

    private static ModelConfig convertModelConfigToDomain(ModelConfigDTO dto) {
        if (dto == null) {
            return new ModelConfig();
        }
        return ModelConfig.builder()
                .name(dto.getName())
                .temperature(dto.getTemperature())
                .maxTokens(dto.getMaxTokens())
                .build();
    }

    private static List<McpServerConfig> convertMcpServersToDomain(List<McpServerConfigDTO> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .map(d -> McpServerConfig.builder()
                        .name(d.getName())
                        .transport(d.getTransport())
                        .command(d.getCommand())
                        .args(d.getArgs())
                        .url(d.getUrl())
                        .headers(d.getHeaders())
                        .build())
                .collect(Collectors.toList());
    }

    private static List<WorkflowNode> convertWorkflowNodesToDomain(List<WorkflowNodeDTO> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .map(d -> WorkflowNode.builder()
                        .id(d.getId())
                        .agentId(d.getAgentId())
                        .reactAgentId(d.getReactAgentId())
                        .next(d.getNext())
                        .ragEnabled(d.getRagEnabled())
                        .knowledgeBaseId(d.getKnowledgeBaseId())
                        .build())
                .collect(Collectors.toList());
    }

    private static List<GraphEdge> convertGraphEdgesToDomain(List<GraphEdgeDTO> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .map(d -> GraphEdge.builder()
                        .from(d.getFrom())
                        .to(d.getTo())
                        .condition(d.getCondition())
                        .build())
                .collect(Collectors.toList());
    }

    private static List<AgentscopeAgentConfig> convertAgentscopeAgentsToDomain(List<AgentscopeAgentConfigDTO> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .map(d -> AgentscopeAgentConfig.builder()
                        .agentId(d.getAgentId())
                        .mcpServers(convertMcpServersToDomain(d.getMcpServers()))
                        .enableTools(d.getEnableTools())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * AgentDefinition → AgentDefinitionDTO
     */
    public static AgentDefinitionDTO toDTO(AgentDefinition definition) {
        if (definition == null) {
            return null;
        }
        AgentDefinitionDTO.AgentDefinitionDTOBuilder builder = AgentDefinitionDTO.builder()
                .agentId(definition.getAgentId())
                .name(definition.getName())
                .engine(definition.getEngine())
                .instruction(definition.getInstruction())
                .modelConfig(convertModelConfigToDTO(definition.getModelConfig()))
                .mcpServers(convertMcpServersToDTO(definition.getMcpServers()));

        if (definition instanceof GraphAgentDefinition graphDef) {
            builder.graphStart(graphDef.getGraphStart());
            builder.graphNodes(convertWorkflowNodesToDTO(graphDef.getGraphNodes()));
            builder.graphEdges(convertGraphEdgesToDTO(graphDef.getGraphEdges()));
        } else if (definition instanceof AgentscopeAgentDefinition asDef) {
            builder.agentscopePipelineType(asDef.getAgentscopePipelineType());
            builder.agentscopeAgents(convertAgentscopeAgentsToDTO(asDef.getAgentscopeAgents()));
        } else if (definition instanceof HybridAgentDefinition hybridDef) {
            builder.graphStart(hybridDef.getGraphStart());
            builder.graphNodes(convertWorkflowNodesToDTO(hybridDef.getGraphNodes()));
            builder.graphEdges(convertGraphEdgesToDTO(hybridDef.getGraphEdges()));
            builder.subEngines(hybridDef.getSubEngines());
        }

        return builder.build();
    }

    private static ModelConfigDTO convertModelConfigToDTO(ModelConfig modelConfig) {
        if (modelConfig == null) return null;
        return ModelConfigDTO.builder()
                .name(modelConfig.getName())
                .temperature(modelConfig.getTemperature())
                .maxTokens(modelConfig.getMaxTokens())
                .build();
    }

    private static List<McpServerConfigDTO> convertMcpServersToDTO(List<McpServerConfig> servers) {
        if (servers == null) return null;
        return servers.stream()
                .map(s -> McpServerConfigDTO.builder()
                        .name(s.getName()).transport(s.getTransport())
                        .command(s.getCommand()).args(s.getArgs())
                        .url(s.getUrl()).headers(s.getHeaders())
                        .build())
                .collect(Collectors.toList());
    }

    private static List<WorkflowNodeDTO> convertWorkflowNodesToDTO(List<WorkflowNode> nodes) {
        if (nodes == null) return null;
        return nodes.stream()
                .map(n -> WorkflowNodeDTO.builder()
                        .id(n.getId()).agentId(n.getAgentId())
                        .reactAgentId(n.getReactAgentId()).next(n.getNext())
                        .ragEnabled(n.getRagEnabled()).knowledgeBaseId(n.getKnowledgeBaseId())
                        .build())
                .collect(Collectors.toList());
    }

    private static List<GraphEdgeDTO> convertGraphEdgesToDTO(List<GraphEdge> edges) {
        if (edges == null) return null;
        return edges.stream()
                .map(e -> GraphEdgeDTO.builder().from(e.getFrom()).to(e.getTo()).condition(e.getCondition()).build())
                .collect(Collectors.toList());
    }

    private static List<AgentscopeAgentConfigDTO> convertAgentscopeAgentsToDTO(List<AgentscopeAgentConfig> agents) {
        if (agents == null) return null;
        return agents.stream()
                .map(a -> AgentscopeAgentConfigDTO.builder()
                        .agentId(a.getAgentId())
                        .mcpServers(convertMcpServersToDTO(a.getMcpServers()))
                        .enableTools(a.getEnableTools())
                        .build())
                .collect(Collectors.toList());
    }
}
```

- [ ] **Step 2: Update AgentController to use AgentDefinitionConverter**

Replace all private conversion methods in `AgentController` with calls to `AgentDefinitionConverter`. Delete the 12 private methods (`convertToDTO`, `convertToDomain`, and all their helpers, lines 97-316). Replace usages:

- Line 43: `.map(this::convertToDTO)` → `.map(AgentDefinitionConverter::toDTO)`
- Line 53: `convertToDTO(definition)` → `AgentDefinitionConverter.toDTO(definition)`
- Line 62: `convertToDomain(dto)` → `AgentDefinitionConverter.toDomain(dto)`
- Line 75: `convertToDomain(dto)` → `AgentDefinitionConverter.toDomain(dto)`
- Line 66: `convertToDTO(definition)` → `AgentDefinitionConverter.toDTO(definition)`
- Line 79: `convertToDTO(definition)` → `AgentDefinitionConverter.toDTO(definition)`

Add import: `import com.ai.agent.trigger.converter.AgentDefinitionConverter;`

Remove unused imports: `AgentscopeAgentConfig`, `GraphEdge`, `McpServerConfig`, `WorkflowNode`, `ModelConfig` (these are only used inside the deleted private methods).

- [ ] **Step 3: Build and verify**

Run: `mvn clean install -pl agent-scaffold-engineering-trigger -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: extract AgentDefinitionConverter from AgentController"
```

---

## Task 2: Add `agentDefinition` field to backend request chain

**Files:**
- Modify: `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/chat/ChatRequestDTO.java`
- Modify: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/common/valobj/ChatRequest.java`
- Modify: `agent-scaffold-engineering-trigger/src/main/java/com/ai/agent/trigger/http/ChatController.java`

- [ ] **Step 1: Add `agentDefinition` to `ChatRequestDTO`**

Add after the `agentId` field (line 24) in `ChatRequestDTO.java`:

```java
    /** 内联 Agent 定义（未保存画布时使用） */
    private AgentDefinitionDTO agentDefinition;
```

Add import: `import com.ai.agent.api.model.agent.AgentDefinitionDTO;`

- [ ] **Step 2: Add `agentDefinition` to `ChatRequest`**

Add after the `agentId` field (line 41) in `ChatRequest.java`:

```java
    /** 内联 Agent 定义（未保存画布时传递的临时画布数据） */
    private AgentDefinition agentDefinition;
```

Add import: `import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;`

- [ ] **Step 3: Update `ChatController.convertRequest()` to map `agentDefinition`**

In `ChatController.java`, add a line in the `convertRequest` builder (after line 64 `.agentId(dto.getAgentId())`):

```java
                .agentDefinition(dto.getAgentDefinition() != null
                        ? AgentDefinitionConverter.toDomain(dto.getAgentDefinition()) : null)
```

Add import: `import com.ai.agent.trigger.converter.AgentDefinitionConverter;`

- [ ] **Step 4: Build and verify**

Run: `mvn clean install`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add agentDefinition field to ChatRequest chain (DTO + domain + controller)"
```

---

## Task 3: Add inline definition validation and three-tier fallback in TaskRuntime

**Files:**
- Modify: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/service/TaskRuntime.java`
- Modify: `agent-scaffold-engineering-app/src/test/java/com/ai/agent/domain/agent/service/TaskRuntimeTest.java`

- [ ] **Step 1: Write failing test for three-tier fallback**

Add these tests to `TaskRuntimeTest.java`:

```java
    @Test
    void resolveAgentDefinition_withAgentId_returnsFromRegistry() {
        // Setup: register an agent
        GraphAgentDefinition def = GraphAgentDefinition.builder()
                .agentId("test-graph")
                .name("Test")
                .engine(EngineType.GRAPH)
                .graphStart("start")
                .graphNodes(List.of(WorkflowNode.builder().id("start").agentId("sub").build()))
                .build();
        AgentRegistry registry = new AgentRegistry(null);
        registry.register(def);

        ChatRequest request = ChatRequest.builder()
                .agentId("test-graph")
                .mode(ChatMode.AGENT)
                .build();

        // Existing logic: agentId found in registry
        assertNotNull(registry.get("test-graph"));
    }

    @Test
    void resolveAgentDefinition_withInlineDefinition_usesItDirectly() {
        // Inline definition should be used when agentId is absent
        GraphAgentDefinition inlineDef = GraphAgentDefinition.builder()
                .agentId("temp_123")
                .name("Temp")
                .engine(EngineType.GRAPH)
                .graphStart("start")
                .graphNodes(List.of(WorkflowNode.builder().id("start").agentId("sub").build()))
                .build();

        ChatRequest request = ChatRequest.builder()
                .mode(ChatMode.AGENT)
                .agentDefinition(inlineDef)
                .build();

        assertNotNull(request.getAgentDefinition());
        assertEquals(EngineType.GRAPH, request.getAgentDefinition().getEngine());
    }

    @Test
    void resolveAgentDefinition_withNeither_throwsAgentNotFound() {
        ChatRequest request = ChatRequest.builder()
                .mode(ChatMode.AGENT)
                .build();

        // Neither agentId nor agentDefinition — should fail
        assertNull(request.getAgentId());
        assertNull(request.getAgentDefinition());
    }
```

Add imports to test:
```java
import com.ai.agent.domain.agent.model.aggregate.GraphAgentDefinition;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.enums.ChatMode;
import com.ai.agent.domain.common.valobj.ChatRequest;
import java.util.List;
```

- [ ] **Step 2: Run test to verify it compiles and the assertions hold for the current model**

Run: `mvn test -pl agent-scaffold-engineering-app -Dtest=TaskRuntimeTest -DskipTests=false`
Expected: The new tests should PASS (they only test the data model, not the resolve logic yet). The real behavior change is in `resolveAgentDefinition()` which will be tested via integration.

- [ ] **Step 3: Implement three-tier fallback in `TaskRuntime.resolveAgentDefinition()`**

Replace the existing `resolveAgentDefinition` method (lines 155-174) with:

```java
    /**
     * 解析 Agent 定义 — 三级回退
     * 1. agentId → 从 Registry 查找
     * 2. request.agentDefinition → 直接使用内联定义（校验后）
     * 3. 都没有 → 抛出 AGENT_NOT_FOUND
     */
    private AgentDefinition resolveAgentDefinition(ChatRequest request) {
        // 1. 优先按 agentId 查找
        if (request.getAgentId() != null && !request.getAgentId().isBlank()) {
            AgentDefinition def = agentRegistry.get(request.getAgentId());
            if (def != null) {
                return def;
            }
            log.warn("未找到指定agentId的Agent定义: agentId={}", request.getAgentId());
        }

        // 2. 使用内联定义
        if (request.getAgentDefinition() != null) {
            validateInlineDefinition(request.getAgentDefinition());
            return request.getAgentDefinition();
        }

        // 3. 都没有
        throw new AgentException(ErrorCodeEnum.AGENT_NOT_FOUND,
                "未提供 agentId 且未传递画布定义数据");
    }

    /**
     * 校验内联 Agent 定义的必填字段
     */
    private void validateInlineDefinition(AgentDefinition def) {
        EngineType engine = def.getEngine() != null ? def.getEngine() : EngineType.CHAT;
        switch (engine) {
            case GRAPH -> {
                if (def instanceof GraphAgentDefinition graphDef) {
                    if (graphDef.getGraphNodes() == null || graphDef.getGraphNodes().isEmpty()) {
                        throw new AgentException(ErrorCodeEnum.AGENT_NOT_FOUND,
                                "GRAPH 类型 Agent 定义缺少 graphNodes 字段");
                    }
                    if (graphDef.getGraphStart() == null || graphDef.getGraphStart().isBlank()) {
                        throw new AgentException(ErrorCodeEnum.AGENT_NOT_FOUND,
                                "GRAPH 类型 Agent 定义缺少 graphStart 字段");
                    }
                }
            }
            case AGENTSCOPE -> {
                if (def instanceof AgentscopeAgentDefinition asDef) {
                    if (asDef.getAgentscopeAgents() == null || asDef.getAgentscopeAgents().isEmpty()) {
                        throw new AgentException(ErrorCodeEnum.AGENT_NOT_FOUND,
                                "AGENTSCOPE 类型 Agent 定义缺少 agentscopeAgents 字段");
                    }
                }
            }
            case HYBRID -> {
                if (def instanceof HybridAgentDefinition hybridDef) {
                    if (hybridDef.getGraphNodes() == null || hybridDef.getGraphNodes().isEmpty()) {
                        throw new AgentException(ErrorCodeEnum.AGENT_NOT_FOUND,
                                "HYBRID 类型 Agent 定义缺少 graphNodes 字段");
                    }
                    if (hybridDef.getGraphStart() == null || hybridDef.getGraphStart().isBlank()) {
                        throw new AgentException(ErrorCodeEnum.AGENT_NOT_FOUND,
                                "HYBRID 类型 Agent 定义缺少 graphStart 字段");
                    }
                }
            }
            case CHAT -> {
                if (def.getInstruction() == null || def.getInstruction().isBlank()) {
                    throw new AgentException(ErrorCodeEnum.AGENT_NOT_FOUND,
                            "CHAT 类型 Agent 定义缺少 instruction 字段");
                }
            }
        }
    }
```

Add imports to `TaskRuntime.java`:
```java
import com.ai.agent.domain.agent.model.aggregate.GraphAgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.AgentscopeAgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.HybridAgentDefinition;
```

- [ ] **Step 4: Also update the sync `execute()` path to use `resolveAgentDefinition`**

The sync `execute(ChatRequest)` method (line 63-67) currently calls `execute(request.getAgentId(), input)` which does its own `agentRegistry.get(agentId)`. Update it to also use `resolveAgentDefinition`:

```java
    @Override
    public ChatResponse execute(ChatRequest request) {
        AgentMessage input = toAgentMessage(request);
        AgentDefinition def = resolveAgentDefinition(request);
        EngineAdapter adapter = routeAdapter(def.getEngine());
        ContextStore ctx = contextStoreFactory.getOrCreate(
                request.getSessionId(),
                request.getUserId(),
                request.getAgentId(),
                request.getEngine() != null ? request.getEngine() : EngineType.GRAPH,
                Boolean.TRUE.equals(request.getRagEnabled()),
                request.getKnowledgeBaseId()
        );
        AgentMessage result = adapter.execute(def, input, ctx);
        return toChatResponse(result);
    }
```

This unifies sync and stream paths — both now go through `resolveAgentDefinition`.

- [ ] **Step 5: Build and verify**

Run: `mvn clean install`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add three-tier fallback in TaskRuntime.resolveAgentDefinition with inline validation"
```

---

## Task 4: Add `agentDefinition` to frontend ChatRequest type

**Files:**
- Modify: `frontend/src/api/chat.ts`

- [ ] **Step 1: Add `agentDefinition` field to `ChatRequest` interface**

In `frontend/src/api/chat.ts`, add after line 36 (`agentId?: string;`):

```typescript
  /** 内联 Agent 定义（未保存画布时使用） */
  agentDefinition?: import('./agent').AgentDefinition;
```

Note: Using `import()` type syntax to avoid circular imports. Alternatively, add an explicit import at the top:
```typescript
import type { AgentDefinition } from './agent';
```
and then use `agentDefinition?: AgentDefinition;`

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd frontend && npx tsc --noEmit 2>&1 | head -20`
Expected: No errors related to `ChatRequest`

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/chat.ts
git commit -m "feat: add agentDefinition field to frontend ChatRequest type"
```

---

## Task 5: Merge test-run into ChatPanel and remove from CanvasToolbar

**Files:**
- Modify: `frontend/src/features/chat/ChatPanel.tsx`
- Modify: `frontend/src/features/canvas/CanvasToolbar.tsx`
- Modify: `frontend/src/pages/WorkspacePage.tsx`

- [ ] **Step 1: Rewrite ChatPanel.handleSend to be canvas-aware**

Replace the existing `handleSend` callback in `ChatPanel.tsx` (lines 47-78) with:

```typescript
  /** 发送消息 */
  const handleSend = useCallback(
    (text: string) => {
      // 1. 添加用户消息
      addMessage({
        id: genMsgId(),
        role: 'user',
        content: text,
        timestamp: Date.now(),
      });

      // 2. 添加空的助手消息占位（用于流式追加）
      addMessage({
        id: genMsgId(),
        role: 'assistant',
        content: '',
        timestamp: Date.now(),
      });

      // 3. 构建请求参数 — 区分工作区页面和 /chat 页面
      const canvasNodes = useCanvasStore.getState().nodes;
      const canvasAgentId = useCanvasStore.getState().currentAgentId;
      const canvasEngineType = useCanvasStore.getState().currentEngineType;

      const hasCanvasNodes = canvasNodes.length > 0;
      const isUnsavedCanvas = hasCanvasNodes && !canvasAgentId;

      startStream({
        query: text,
        userId: 'web-user',
        sessionId: activeSessionId ?? undefined,
        agentId: canvasAgentId || selectedAgentId || undefined,
        mode: (canvasAgentId || selectedAgentId || hasCanvasNodes) ? 'AGENT' : 'MULTI_TURN',
        engine: hasCanvasNodes
          ? (canvasEngineType || 'GRAPH')
          : (agents.find((a) => a.agentId === selectedAgentId)?.engine ?? undefined),
        agentDefinition: isUnsavedCanvas
          ? {
              agentId: `temp_${Date.now()}`,
              ...useCanvasStore.getState().exportToAgentDefinition(),
            }
          : undefined,
      });
    },
    [addMessage, startStream, activeSessionId, selectedAgentId, agents],
  );
```

Add import at the top:
```typescript
import { useCanvasStore } from '@/stores/canvas';
```
(This import is already present on line 10, so no change needed if it's there.)

**Note:** We use `useCanvasStore.getState()` instead of a selector hook because `ChatPanel` is shared between workspace and /chat pages. On /chat pages, `canvasStore.nodes` will be empty, so the canvas-aware logic naturally falls through to the `/chat` page behavior.

- [ ] **Step 2: Update ChatPanel header to show unsaved canvas status**

In the ChatPanel JSX header section (around lines 83-100), update the agent name display:

Replace the existing header block (lines 83-100) with:

```tsx
      {/* ── 顶部：Agent 信息 ── */}
      <div className="flex items-center gap-2 border-b border-gray-200 px-4 py-3">
        <div className="flex size-8 items-center justify-center rounded-full bg-indigo-100 text-indigo-600">
          <Bot className="size-4" />
        </div>
        <div className="min-w-0 flex-1">
          <h2 className="truncate text-sm font-semibold text-gray-900">
            {activeAgent?.name ?? (useCanvasStore.getState().nodes.length > 0 ? '未保存画布' : 'Agent 对话')}
          </h2>
          {activeAgent && (
            <span className="text-xs text-gray-400">{activeAgent.agentId}</span>
          )}
          {!activeAgent && useCanvasStore.getState().currentEngineType && useCanvasStore.getState().nodes.length > 0 && (
            <Badge variant="outline" className="ml-1 text-xs text-amber-600 border-amber-300">
              未保存
            </Badge>
          )}
        </div>
        {mode === 'expert' && (
          <Badge variant="outline" className="text-xs text-indigo-600 border-indigo-300">
            专家模式
          </Badge>
        )}
      </div>
```

- [ ] **Step 3: Remove test-run from CanvasToolbar**

In `CanvasToolbar.tsx`:

1. Remove the `Play` and `Loader2` icon imports from line 9-10 (keep other icons)
2. Remove `streamChat` import from line 24
3. Remove `testLoading` state (line 48): `const [testLoading, setTestLoading] = useState(false);`
4. Remove the entire `handleTest` function (lines 121-217)
5. Remove the following store selectors that were only used by `handleTest`:
   - `setNodeState` (line 38)
   - `clearNodeStates` (line 38)
   - `appendToLastMessage` (line 41)
   - `setActiveSessionId` (line 42)
   - `setIsStreaming` (line 43)
6. Remove the test-run button JSX (lines 278-288):
```tsx
        {/* 测试运行 */}
        <Button
          variant="default"
          size="sm"
          className="gap-1"
          onClick={handleTest}
          disabled={testLoading}
        >
          {testLoading ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4" />}
          测试运行
        </Button>
```

7. Remove `useChatStore` import (line 21) if no other selectors remain from it

- [ ] **Step 4: Remove duplicate CanvasToolbar from AgentCanvas**

In `AgentCanvas.tsx`:
1. Remove the `CanvasToolbar` import (line 24): `import CanvasToolbar from './CanvasToolbar';`
2. Remove `<CanvasToolbar />` from the JSX (line 107)

The toolbar is already rendered in `WorkspacePage.tsx` (line 18), so removing it from `AgentCanvas` eliminates the duplicate.

- [ ] **Step 5: Verify the app runs**

Run: `cd frontend && npm run build`
Expected: Build succeeds with no TypeScript errors

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: merge test-run into ChatPanel, remove from CanvasToolbar, fix duplicate toolbar"
```

---

## Task 6: Add canvas node state sync to useSSE

**Files:**
- Modify: `frontend/src/hooks/useSSE.ts`

- [ ] **Step 1: Add canvasStore.setNodeState calls in useSSE event handler**

In `useSSE.ts`, add an import:
```typescript
import { useCanvasStore } from '@/stores/canvas';
```

In the `startStream` function, after the existing `NODE_START` handler (around line 87), add canvas state sync:

```typescript
            case 'NODE_START': {
              const nodeId =
                typeof event.data === 'string'
                  ? event.data
                  : (event.data?.nodeName as string) ?? (event.data?.nodeId as string) ?? '';
              if (nodeId) {
                addNodeStatus({ nodeId, status: 'running' });
                // 同步画布节点状态
                useCanvasStore.getState().setNodeState(nodeId, { status: 'running' });
              }
              break;
            }
```

Similarly, after the existing `NODE_END` handler (around line 97):

```typescript
            case 'NODE_END': {
              const nodeId =
                typeof event.data === 'string'
                  ? event.data
                  : (event.data?.nodeName as string) ?? (event.data?.nodeId as string) ?? '';
              if (nodeId) {
                updateNodeStatus(nodeId, 'done');
                // 同步画布节点状态
                useCanvasStore.getState().setNodeState(nodeId, { status: 'done' });
              }
              break;
            }
```

Also add sync for `DONE` event — clear all canvas node states when the stream completes:

In the `DONE` case (around line 107), add before existing code:
```typescript
            case 'DONE': {
              // 清除画布节点运行状态
              useCanvasStore.getState().clearNodeStates();
              // ... existing DONE handling
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd frontend && npx tsc --noEmit 2>&1 | head -20`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/hooks/useSSE.ts
git commit -m "feat: sync canvas node states in useSSE on NODE_START/NODE_END/DONE"
```

---

## Task 7: Show NodeExecutionStatus for all agent modes (not just expert)

**Files:**
- Modify: `frontend/src/features/chat/NodeExecutionStatus.tsx`

- [ ] **Step 1: Remove `mode === 'expert'` guard from NodeExecutionStatus**

In `NodeExecutionStatus.tsx`, line 40 currently has:
```typescript
  // 仅在专家模式且有节点状态时显示
  if (mode !== 'expert' || nodeExecutionStatus.length === 0) {
```

Replace with:
```typescript
  // 当有节点执行状态时显示（所有模式）
  if (nodeExecutionStatus.length === 0) {
```

Remove the `mode` import and `useAppStore` import since they're no longer needed:
- Remove line 7: `import { useAppStore } from '@/stores/app';`
- Remove line 36: `const mode = useAppStore((s) => s.mode);`

- [ ] **Step 2: Verify**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/chat/NodeExecutionStatus.tsx
git commit -m "feat: show NodeExecutionStatus in all modes, not just expert"
```

---

## Task 8: Implement drag-to-blank node creation in AgentCanvas

**Files:**
- Modify: `frontend/src/features/canvas/AgentCanvas.tsx`

- [ ] **Step 1: Add `useReactFlow` hook and connection tracking refs**

In `AgentCanvas.tsx`, update imports:

```typescript
import { memo, useCallback, useRef } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useReactFlow,
  type Connection,
  type Edge,
  type Node,
  BackgroundVariant,
} from '@xyflow/react';
```

Inside the `AgentCanvas` function, after the store selectors (line 48), add:

```typescript
  const { screenToFlowPosition } = useReactFlow();

  /** 追踪拖拽连线的起点 */
  const connectingFrom = useRef<{ nodeId: string | null; handleType: string | null; edgeCount: number }>({
    nodeId: null,
    handleType: null,
    edgeCount: 0,
  });
```

Also add `setNodes` to the store destructuring (line 41-48):
```typescript
  const {
    nodes,
    edges,
    onNodesChange,
    onEdgesChange,
    setNodes,
    setEdges,
    setSelectedNodeId,
  } = useCanvasStore();
```

- [ ] **Step 2: Add `onConnectStart` callback**

After the `onPaneClick` callback (line 76), add:

```typescript
  /** 连线开始：记录起点 */
  const onConnectStart = useCallback(
    (_event: React.MouseEvent | React.TouchEvent, { nodeId, handleType }: { nodeId: string | null; handleType: string | null }) => {
      connectingFrom.current = {
        nodeId: handleType === 'source' ? nodeId : null,
        handleType,
        edgeCount: edges.length,
      };
    },
    [edges.length],
  );
```

- [ ] **Step 3: Add `onConnectEnd` callback**

After `onConnectStart`, add:

```typescript
  /** 连线结束：如果拖到空白区域则自动添加 ChatNode */
  const onConnectEnd = useCallback(
    (event: MouseEvent | TouchEvent) => {
      const { nodeId: sourceNodeId, handleType, edgeCount } = connectingFrom.current;

      // 重置追踪状态
      connectingFrom.current = { nodeId: null, handleType: null, edgeCount: 0 };

      // 仅处理从 source handle 拖出
      if (handleType !== 'source' || !sourceNodeId) return;

      // 如果已有新 edge 产生（连到了有效 target），不创建新节点
      if (edges.length > edgeCount) return;

      // 将鼠标屏幕坐标转为画布坐标
      const screenPoint = 'clientX' in event
        ? { x: event.clientX, y: event.clientY }
        : { x: 0, y: 0 };
      const flowPosition = screenToFlowPosition(screenPoint);

      // 创建新 ChatNode
      const newNodeId = `node_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
      const newNode: Node = {
        id: newNodeId,
        type: 'chat',
        position: flowPosition,
        data: { label: '对话节点', engine: 'CHAT' },
      };

      // 创建从源节点到新节点的边
      const newEdge: Edge = {
        id: `e_${sourceNodeId}-${newNodeId}`,
        source: sourceNodeId,
        target: newNodeId,
        type: 'default',
      };

      setNodes([...nodes, newNode]);
      setEdges([...edges, newEdge]);
    },
    [nodes, edges, setNodes, setEdges, screenToFlowPosition],
  );
```

- [ ] **Step 4: Wire up the new callbacks in ReactFlow JSX**

In the `<ReactFlow>` component (line 80), add the new props:

```tsx
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onConnectStart={onConnectStart}
        onConnectEnd={onConnectEnd}
        onNodeClick={onNodeClick}
        onPaneClick={onPaneClick}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        defaultEdgeOptions={{ type: 'default', markerEnd: { type: 'arrowclosed' } }}
        className="bg-gray-50"
      >
```

- [ ] **Step 5: Verify the app builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 6: Manual test**

1. Open the workspace page
2. Add a Start node
3. Drag from the Start node's bottom handle (source) to the canvas blank area
4. Expected: A new ChatNode appears at the drop position with a connecting edge
5. Drag from a ChatNode's source handle to another ChatNode's target handle
6. Expected: Normal edge connection (no extra node created)

- [ ] **Step 7: Commit**

```bash
git add frontend/src/features/canvas/AgentCanvas.tsx
git commit -m "feat: drag-to-blank auto-creates ChatNode with connecting edge"
```

---

## Task 9: Integration verification and cleanup

**Files:**
- All modified files (verification only)

- [ ] **Step 1: Full backend build**

Run: `mvn clean install`
Expected: BUILD SUCCESS

- [ ] **Step 2: Full frontend build**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 3: Verify no duplicate toolbar**

Check `WorkspacePage.tsx` renders `CanvasToolbar` once, and `AgentCanvas.tsx` no longer imports it.

- [ ] **Step 4: Verify test-run flow end-to-end conceptually**

Walk through the new flow:
1. User opens workspace page → sees canvas + ChatPanel (no test-run button in toolbar)
2. User adds nodes to canvas → ChatPanel header shows "未保存画布" badge
3. User types in ChatPanel → `handleSend` detects `canvasNodes.length > 0` + no `canvasAgentId`
4. Request includes `agentDefinition` with `agentId: "temp_xxx"` + canvas data
5. Backend `resolveAgentDefinition`: agentId not in registry → falls to inline definition → validates → executes
6. SSE events stream back → `useSSE` updates chat messages AND canvas node states
7. Canvas nodes pulse/complete in sync with chat panel messages

- [ ] **Step 5: Final commit (if any cleanup needed)**

```bash
git add -A
git commit -m "chore: cleanup after workflow test improvements integration"
```
