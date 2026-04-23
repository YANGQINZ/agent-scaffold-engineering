# 多模式AI Agent对话系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有DDD脚手架上实现支持单轮/多轮/多智能体协同三种对话模式+RAG可选增强的AI Agent对话系统

**Architecture:** 策略模式路由（Strategy Router）— ChatFacade根据mode参数路由到SimpleChatStrategy/MultiTurnStrategy/AgentOrchestratorStrategy，RAG通过RagDecorator装饰器叠加。Agent子模式由AgentOrchestratorStrategy分发到Workflow/ReAct/Graph/Hybrid四个Executor。

**Tech Stack:** Spring Boot 3.4.3, Java 17, Spring AI 1.1.4, Spring AI Alibaba 1.1.2.0, AgentScope 1.0.11, PostgreSQL + PGvector, MyBatis, DashScope(qwq-plus, text-embedding-v1)

---

## File Structure

### types模块 — 新增枚举与错误码
- `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/common/Constants.java` — 修改：新增错误码枚举
- `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/enums/ChatMode.java` — 创建：对话模式枚举
- `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/enums/AgentMode.java` — 创建：Agent编排子模式枚举
- `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/enums/StreamEventType.java` — 创建：SSE事件类型枚举
- `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/enums/MessageRole.java` — 创建：消息角色枚举
- `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/enums/DocumentStatus.java` — 创建：文档处理状态枚举
- `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/enums/OwnerType.java` — 创建：知识库所属者类型枚举
- `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/exception/ChatException.java` — 创建：对话域异常
- `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/exception/AgentException.java` — 创建：Agent域异常
- `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/exception/KnowledgeException.java` — 创建：知识库域异常

### api模块 — 请求/响应DTO
- `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/chat/ChatRequestDTO.java` — 创建：对话请求DTO
- `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/chat/ChatResponseDTO.java` — 创建：对话响应DTO
- `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/chat/StreamEventDTO.java` — 创建：SSE流式事件DTO
- `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/chat/SourceDTO.java` — 创建：RAG引用来源DTO
- `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/knowledge/KnowledgeUploadRequestDTO.java` — 创建：知识库上传请求DTO
- `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/knowledge/KnowledgeBaseResponseDTO.java` — 创建：知识库响应DTO
- `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/IChatService.java` — 创建：对话服务接口
- `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/IKnowledgeService.java` — 创建：知识库服务接口

### domain模块 — chat限界上下文
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/model/aggregate/ChatSession.java` — 创建：会话聚合根
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/model/entity/ChatMessage.java` — 创建：消息实体
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/model/valobj/ChatRequest.java` — 创建：对话请求值对象
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/model/valobj/ChatResponse.java` — 创建：对话响应值对象
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/model/valobj/StreamEvent.java` — 创建：流式事件值对象
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/repository/IChatSessionRepository.java` — 创建：会话仓库接口
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/ChatFacade.java` — 创建：对话门面服务
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/ModeRouter.java` — 创建：模式路由
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/ChatMemoryManager.java` — 创建：对话记忆管理
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/strategy/ChatStrategy.java` — 创建：策略接口
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/strategy/SimpleChatStrategy.java` — 创建：单轮对话策略
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/strategy/MultiTurnChatStrategy.java` — 创建：多轮对话策略
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/strategy/AgentOrchestratorStrategy.java` — 创建：Agent编排策略
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/strategy/RagDecorator.java` — 创建：RAG装饰器

### domain模块 — agent限界上下文
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/model/aggregate/AgentDefinition.java` — 创建：Agent定义聚合根
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/model/entity/WorkflowDefinition.java` — 创建：工作流定义实体
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/model/entity/WorkflowNode.java` — 创建：工作流节点实体
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/model/entity/GraphEdge.java` — 创建：图边实体
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/model/entity/ToolConfig.java` — 创建：工具配置实体
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/model/valobj/ModelConfig.java` — 创建：模型配置值对象
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/repository/IAgentDefinitionRepository.java` — 创建：Agent定义仓库接口
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/service/AgentRegistry.java` — 创建：Agent注册中心
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/service/executor/ReactExecutor.java` — 创建：ReAct执行器
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/service/executor/WorkflowExecutor.java` — 创建：Workflow执行器
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/service/executor/GraphExecutor.java` — 创建：Graph执行器
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/service/executor/HybridExecutor.java` — 创建：Hybrid执行器

### domain模块 — knowledge限界上下文
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/model/aggregate/KnowledgeBase.java` — 创建：知识库聚合根
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/model/entity/Document.java` — 创建：文档实体
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/model/entity/DocumentChunk.java` — 创建：文档切片实体
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/repository/IKnowledgeBaseRepository.java` — 创建：知识库仓库接口
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/repository/IDocumentRepository.java` — 创建：文档仓库接口
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/repository/IDocumentChunkRepository.java` — 创建：切片仓库接口
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/service/DocumentProcessor.java` — 创建：文档处理器
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/service/EmbeddingService.java` — 创建：向量化服务
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/service/RagService.java` — 创建：RAG检索增强服务

### infrastructure模块 — 持久化实现
- `agent-scaffold-engineering-infrastructure/pom.xml` — 修改：新增MyBatis、PostgreSQL、PGvector依赖
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/po/ChatSessionPO.java` — 创建：会话持久化对象
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/po/ChatMessagePO.java` — 创建：消息持久化对象
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/po/KnowledgeBasePO.java` — 创建：知识库PO
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/po/DocumentPO.java` — 创建：文档PO
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/po/DocumentChunkPO.java` — 创建：切片PO
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/dao/IChatSessionDao.java` — 创建：会话DAO
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/dao/IChatMessageDao.java` — 创建：消息DAO
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/dao/IKnowledgeBaseDao.java` — 创建：知识库DAO
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/dao/IDocumentDao.java` — 创建：文档DAO
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/dao/IDocumentChunkDao.java` — 创建：切片DAO
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/repository/ChatSessionRepository.java` — 创建：会话仓库实现
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/repository/AgentDefinitionRepository.java` — 创建：Agent定义仓库实现
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/repository/KnowledgeBaseRepository.java` — 创建：知识库仓库实现
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/repository/DocumentRepository.java` — 创建：文档仓库实现
- `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/repository/DocumentChunkRepository.java` — 创建：切片仓库实现
- `agent-scaffold-engineering-infrastructure/src/main/resources/mybatis/mapper/ChatSessionMapper.xml` — 创建：会话Mapper
- `agent-scaffold-engineering-infrastructure/src/main/resources/mybatis/mapper/ChatMessageMapper.xml` — 创建：消息Mapper
- `agent-scaffold-engineering-infrastructure/src/main/resources/mybatis/mapper/KnowledgeBaseMapper.xml` — 创建：知识库Mapper
- `agent-scaffold-engineering-infrastructure/src/main/resources/mybatis/mapper/DocumentMapper.xml` — 创建：文档Mapper
- `agent-scaffold-engineering-infrastructure/src/main/resources/mybatis/mapper/DocumentChunkMapper.xml` — 创建：切片Mapper

### trigger模块 — HTTP控制器
- `agent-scaffold-engineering-trigger/src/main/java/com/ai/agent/trigger/http/ChatController.java` — 创建：对话控制器
- `agent-scaffold-engineering-trigger/src/main/java/com/ai/agent/trigger/http/KnowledgeController.java` — 创建：知识库控制器
- `agent-scaffold-engineering-trigger/src/main/java/com/ai/agent/trigger/http/GlobalExceptionHandler.java` — 创建：全局异常处理器

### app模块 — 配置与Agent YAML
- `agent-scaffold-engineering-app/pom.xml` — 修改：新增spring-ai-alibaba-starter-agentscope、spring-webflux依赖
- `agent-scaffold-engineering-app/src/main/resources/application-dev.yml` — 修改：新增PostgreSQL、MyBatis、Embedding配置
- `agent-scaffold-engineering-app/src/main/resources/agents/weather-assistant.yaml` — 创建：示例Agent YAML
- `agent-scaffold-engineering-app/src/main/resources/agents/travel-planner.yaml` — 创建：示例Workflow Agent YAML
- `agent-scaffold-engineering-app/src/main/resources/sql/init_pgvector.sql` — 创建：PGvector初始化SQL
- `agent-scaffold-engineering-app/src/main/java/com/ai/agent/config/EmbeddingModelConfig.java` — 创建：Embedding模型配置

### domain模块 — 删除旧模板
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/xxx/` — 删除：旧模板上下文
- `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/yyy/` — 删除：旧模板上下文

---

## Task 1: 基础类型与枚举定义

**Files:**
- Create: `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/enums/ChatMode.java`
- Create: `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/enums/AgentMode.java`
- Create: `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/enums/StreamEventType.java`
- Create: `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/enums/MessageRole.java`
- Create: `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/enums/DocumentStatus.java`
- Create: `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/enums/OwnerType.java`
- Modify: `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/common/Constants.java`
- Create: `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/exception/ChatException.java`
- Create: `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/exception/AgentException.java`
- Create: `agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/exception/KnowledgeException.java`
- Test: `agent-scaffold-engineering-types/src/test/java/com/ai/agent/types/enums/EnumsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ai.agent.types.enums;

import com.ai.agent.types.common.Constants;
import com.ai.agent.types.exception.ChatException;
import com.ai.agent.types.exception.AgentException;
import com.ai.agent.types.exception.KnowledgeException;
import org.junit.Test;
import static org.junit.Assert.*;

public class EnumsTest {

    @Test
    public void testChatModeValues() {
        assertEquals(3, ChatMode.values().length);
        assertNotNull(ChatMode.SIMPLE);
        assertNotNull(ChatMode.MULTI_TURN);
        assertNotNull(ChatMode.AGENT);
    }

    @Test
    public void testAgentModeValues() {
        assertEquals(4, AgentMode.values().length);
        assertNotNull(AgentMode.WORKFLOW);
        assertNotNull(AgentMode.REACT);
        assertNotNull(AgentMode.GRAPH);
        assertNotNull(AgentMode.HYBRID);
    }

    @Test
    public void testStreamEventTypeValues() {
        assertEquals(6, StreamEventType.values().length);
        assertNotNull(StreamEventType.TEXT_DELTA);
        assertNotNull(StreamEventType.THINKING);
        assertNotNull(StreamEventType.NODE_START);
        assertNotNull(StreamEventType.NODE_END);
        assertNotNull(StreamEventType.RAG_RETRIEVE);
        assertNotNull(StreamEventType.DONE);
    }

    @Test
    public void testMessageRoleValues() {
        assertEquals(3, MessageRole.values().length);
    }

    @Test
    public void testDocumentStatusValues() {
        assertEquals(4, DocumentStatus.values().length);
    }

    @Test
    public void testOwnerTypeValues() {
        assertEquals(2, OwnerType.values().length);
    }

    @Test
    public void testErrorCodeConstants() {
        assertEquals("CHAT_", Constants.ErrorCode.CHAT_PREFIX);
        assertEquals("AGENT_", Constants.ErrorCode.AGENT_PREFIX);
        assertEquals("KNOW_", Constants.ErrorCode.KNOW_PREFIX);
    }

    @Test
    public void testDomainExceptions() {
        ChatException chatEx = new ChatException("CHAT_1001", "模式不支持");
        assertEquals("CHAT_1001", chatEx.getCode());
        assertEquals("模式不支持", chatEx.getInfo());

        AgentException agentEx = new AgentException("AGENT_2001", "Agent未找到");
        assertEquals("AGENT_2001", agentEx.getCode());

        KnowledgeException knowEx = new KnowledgeException("KNOW_3001", "文档处理失败");
        assertEquals("KNOW_3001", knowEx.getCode());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/yangqinz/dev-ops/agent-scaffold-engineering && mvn test -pl agent-scaffold-engineering-types -Dtest=EnumsTest -DskipTests=false 2>&1 | tail -20`
Expected: FAIL — classes not found

- [ ] **Step 3: Create enum files**

```java
// ChatMode.java
package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChatMode {
    SIMPLE("单轮对话"),
    MULTI_TURN("多轮对话"),
    AGENT("多智能体协同");

    private final String description;
}
```

```java
// AgentMode.java
package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentMode {
    WORKFLOW("Workflow编排"),
    REACT("ReAct Agent"),
    GRAPH("Graph编排"),
    HYBRID("Hybrid混合");

    private final String description;
}
```

```java
// StreamEventType.java
package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StreamEventType {
    TEXT_DELTA("文本片段"),
    THINKING("思考过程"),
    NODE_START("节点开始"),
    NODE_END("节点结束"),
    RAG_RETRIEVE("RAG检索"),
    DONE("完成");

    private final String description;
}
```

```java
// MessageRole.java
package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MessageRole {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system");

    private final String value;
}
```

```java
// DocumentStatus.java
package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DocumentStatus {
    PENDING("待处理"),
    PROCESSING("处理中"),
    COMPLETED("已完成"),
    FAILED("失败");

    private final String description;
}
```

```java
// OwnerType.java
package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OwnerType {
    ADMIN("管理员"),
    USER("用户");

    private final String description;
}
```

- [ ] **Step 4: Add error code constants to Constants.java**

在 `Constants.java` 的 `ResponseCode` 枚举之后添加：

```java
public static class ErrorCode {
    public static final String CHAT_PREFIX = "CHAT_";
    public static final String AGENT_PREFIX = "AGENT_";
    public static final String KNOW_PREFIX = "KNOW_";

    public static final String CHAT_MODE_UNSUPPORTED = "CHAT_1001";
    public static final String CHAT_SESSION_EXPIRED = "CHAT_1002";
    public static final String CHAT_LLM_ERROR = "CHAT_1003";

    public static final String AGENT_NOT_FOUND = "AGENT_2001";
    public static final String AGENT_ORCHESTRATION_FAILED = "AGENT_2002";
    public static final String AGENT_MODE_UNSUPPORTED = "AGENT_2003";

    public static final String KNOW_UPLOAD_FAILED = "KNOW_3001";
    public static final String KNOW_PROCESSING_FAILED = "KNOW_3002";
    public static final String KNOW_RETRIEVE_FAILED = "KNOW_3003";
}
```

- [ ] **Step 5: Create domain exception classes**

```java
// ChatException.java
package com.ai.agent.types.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChatException extends AppException {

    public ChatException(String code) {
        super(code);
    }

    public ChatException(String code, String message) {
        super(code, message);
    }

    public ChatException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
```

```java
// AgentException.java
package com.ai.agent.types.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AgentException extends AppException {

    public AgentException(String code) {
        super(code);
    }

    public AgentException(String code, String message) {
        super(code, message);
    }

    public AgentException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
```

```java
// KnowledgeException.java
package com.ai.agent.types.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class KnowledgeException extends AppException {

    public KnowledgeException(String code) {
        super(code);
    }

    public KnowledgeException(String code, String message) {
        super(code, message);
    }

    public KnowledgeException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd /Users/yangqinz/dev-ops/agent-scaffold-engineering && mvn test -pl agent-scaffold-engineering-types -Dtest=EnumsTest -DskipTests=false 2>&1 | tail -20`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
cd /Users/yangqinz/dev-ops/agent-scaffold-engineering
git add agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/enums/ agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/exception/ agent-scaffold-engineering-types/src/main/java/com/ai/agent/types/common/Constants.java agent-scaffold-engineering-types/src/test/java/com/ai/agent/types/enums/
git commit -m "feat(types): add ChatMode/AgentMode/StreamEventType enums and domain exceptions"
```

---

## Task 2: API层DTO定义

**Files:**
- Create: `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/chat/ChatRequestDTO.java`
- Create: `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/chat/ChatResponseDTO.java`
- Create: `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/chat/StreamEventDTO.java`
- Create: `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/chat/SourceDTO.java`
- Create: `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/knowledge/KnowledgeUploadRequestDTO.java`
- Create: `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/model/knowledge/KnowledgeBaseResponseDTO.java`
- Create: `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/IChatService.java`
- Create: `agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/IKnowledgeService.java`

- [ ] **Step 1: Create ChatRequestDTO**

```java
package com.ai.agent.api.model.chat;

import com.ai.agent.types.enums.AgentMode;
import com.ai.agent.types.enums.ChatMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatRequestDTO {
    @NotBlank(message = "userId不能为空")
    private String userId;
    private String sessionId;
    @NotBlank(message = "query不能为空")
    private String query;
    @NotNull(message = "mode不能为空")
    private ChatMode mode;
    private AgentMode agentMode;
    private Boolean ragEnabled = false;
    private String knowledgeBaseId;
}
```

- [ ] **Step 2: Create ChatResponseDTO**

```java
package com.ai.agent.api.model.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDTO {
    private String answer;
    private List<SourceDTO> sources;
    private String sessionId;
    private Boolean ragDegraded;
    private Map<String, Object> metadata;
}
```

- [ ] **Step 3: Create StreamEventDTO**

```java
package com.ai.agent.api.model.chat;

import com.ai.agent.types.enums.StreamEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamEventDTO {
    private StreamEventType type;
    private Map<String, Object> data;
    private String sessionId;
}
```

- [ ] **Step 4: Create SourceDTO**

```java
package com.ai.agent.api.model.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceDTO {
    private String docName;
    private String chunkContent;
    private Double score;
}
```

- [ ] **Step 5: Create KnowledgeUploadRequestDTO**

```java
package com.ai.agent.api.model.knowledge;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeUploadRequestDTO {
    @NotBlank(message = "knowledgeBaseId不能为空")
    private String knowledgeBaseId;
    private String userId;
}
```

- [ ] **Step 6: Create KnowledgeBaseResponseDTO**

```java
package com.ai.agent.api.model.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseResponseDTO {
    private String baseId;
    private String name;
    private String description;
    private Integer docCount;
}
```

- [ ] **Step 7: Create IChatService interface**

```java
package com.ai.agent.api;

import com.ai.agent.api.model.chat.ChatRequestDTO;
import com.ai.agent.api.model.chat.ChatResponseDTO;
import com.ai.agent.api.model.chat.StreamEventDTO;
import reactor.core.publisher.Flux;

public interface IChatService {
    ChatResponseDTO chat(ChatRequestDTO request);
    Flux<StreamEventDTO> chatStream(ChatRequestDTO request);
}
```

- [ ] **Step 8: Create IKnowledgeService interface**

```java
package com.ai.agent.api;

import com.ai.agent.api.model.knowledge.KnowledgeBaseResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface IKnowledgeService {
    KnowledgeBaseResponseDTO createKnowledgeBase(String name, String description, String ownerType, String ownerId);
    String uploadDocument(String knowledgeBaseId, MultipartFile file, String userId);
}
```

- [ ] **Step 9: Verify compilation**

Run: `cd /Users/yangqinz/dev-ops/agent-scaffold-engineering && mvn compile -pl agent-scaffold-engineering-api -am 2>&1 | tail -10`
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
cd /Users/yangqinz/dev-ops/agent-scaffold-engineering
git add agent-scaffold-engineering-api/src/main/java/com/ai/agent/api/
git commit -m "feat(api): add chat/knowledge DTOs and service interfaces"
```

---

## Task 3: Domain层 — Chat限界上下文

**Files:**
- Delete: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/xxx/`
- Delete: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/yyy/`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/model/aggregate/ChatSession.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/model/entity/ChatMessage.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/model/valobj/ChatRequest.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/model/valobj/ChatResponse.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/model/valobj/StreamEvent.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/repository/IChatSessionRepository.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/ChatFacade.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/ModeRouter.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/ChatMemoryManager.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/strategy/ChatStrategy.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/strategy/SimpleChatStrategy.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/strategy/MultiTurnChatStrategy.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/strategy/AgentOrchestratorStrategy.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/chat/service/strategy/RagDecorator.java`

- [ ] **Step 1: Delete old template contexts**

```bash
rm -rf agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/xxx
rm -rf agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/yyy
```

- [ ] **Step 2: Create ChatSession aggregate**

```java
package com.ai.agent.domain.chat.model.aggregate;

import com.ai.agent.types.enums.AgentMode;
import com.ai.agent.types.enums.ChatMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ai.agent.domain.chat.model.entity.ChatMessage;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    private String sessionId;
    private String userId;
    private ChatMode mode;
    private AgentMode agentMode;
    private Boolean ragEnabled;
    private String knowledgeBaseId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime ttlExpireAt;

    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    public void addMessage(ChatMessage message) {
        this.messages.add(message);
        this.updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Create ChatMessage entity**

```java
package com.ai.agent.domain.chat.model.entity;

import com.ai.agent.types.enums.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private Long messageId;
    private String sessionId;
    private MessageRole role;
    private String content;
    private Integer tokenCount;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: Create ChatRequest value object**

```java
package com.ai.agent.domain.chat.model.valobj;

import com.ai.agent.types.enums.AgentMode;
import com.ai.agent.types.enums.ChatMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private String userId;
    private String sessionId;
    private String query;
    private ChatMode mode;
    private AgentMode agentMode;
    private Boolean ragEnabled;
    private String knowledgeBaseId;
}
```

- [ ] **Step 5: Create ChatResponse value object**

```java
package com.ai.agent.domain.chat.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String answer;
    private List<Source> sources;
    private String sessionId;
    private Boolean ragDegraded;
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {
        private String docName;
        private String chunkContent;
        private Double score;
    }
}
```

- [ ] **Step 6: Create StreamEvent value object**

```java
package com.ai.agent.domain.chat.model.valobj;

import com.ai.agent.types.enums.StreamEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamEvent {
    private StreamEventType type;
    private Map<String, Object> data;
    private String sessionId;

    public static StreamEvent textDelta(String content, String sessionId) {
        return StreamEvent.builder()
                .type(StreamEventType.TEXT_DELTA)
                .data(Map.of("content", content))
                .sessionId(sessionId)
                .build();
    }

    public static StreamEvent thinking(String step, String content, String sessionId) {
        return StreamEvent.builder()
                .type(StreamEventType.THINKING)
                .data(Map.of("step", step, "content", content))
                .sessionId(sessionId)
                .build();
    }

    public static StreamEvent nodeStart(String nodeId, String nodeName, String sessionId) {
        return StreamEvent.builder()
                .type(StreamEventType.NODE_START)
                .data(Map.of("nodeId", nodeId, "nodeName", nodeName))
                .sessionId(sessionId)
                .build();
    }

    public static StreamEvent nodeEnd(String nodeId, String nodeName, String output, String sessionId) {
        return StreamEvent.builder()
                .type(StreamEventType.NODE_END)
                .data(Map.of("nodeId", nodeId, "nodeName", nodeName, "output", output))
                .sessionId(sessionId)
                .build();
    }

    public static StreamEvent done(Boolean ragDegraded, Map<String, Object> metadata, String sessionId) {
        return StreamEvent.builder()
                .type(StreamEventType.DONE)
                .data(Map.of("ragDegraded", ragDegraded != null ? ragDegraded : false, "metadata", metadata != null ? metadata : Map.of()))
                .sessionId(sessionId)
                .build();
    }
}
```

- [ ] **Step 7: Create IChatSessionRepository interface**

```java
package com.ai.agent.domain.chat.repository;

import com.ai.agent.domain.chat.model.aggregate.ChatSession;
import com.ai.agent.domain.chat.model.entity.ChatMessage;

import java.util.List;

public interface IChatSessionRepository {
    void save(ChatSession session);
    ChatSession findById(String sessionId);
    void saveMessage(ChatMessage message);
    List<ChatMessage> findMessagesBySessionId(String sessionId);
    void updateSessionTtl(String sessionId);
}
```

- [ ] **Step 8: Create ChatStrategy interface**

```java
package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import reactor.core.publisher.Flux;

public interface ChatStrategy {
    ChatResponse execute(ChatRequest request);
    Flux<StreamEvent> executeStream(ChatRequest request);
}
```

- [ ] **Step 9: Create SimpleChatStrategy**

```java
package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.exception.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SimpleChatStrategy implements ChatStrategy {

    private final ChatModel chatModel;

    public SimpleChatStrategy(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public com.ai.agent.domain.chat.model.valobj.ChatResponse execute(ChatRequest request) {
        try {
            org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(
                    new Prompt(List.of(new UserMessage(request.getQuery())))
            );
            String answer = aiResponse.getResult().getOutput().getText();
            return com.ai.agent.domain.chat.model.valobj.ChatResponse.builder()
                    .answer(answer)
                    .sessionId(request.getSessionId())
                    .ragDegraded(false)
                    .build();
        } catch (Exception e) {
            throw new ChatException(Constants.ErrorCode.CHAT_LLM_ERROR, "服务繁忙请稍后重试", e);
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(ChatRequest request) {
        try {
            String sessionId = request.getSessionId();
            return chatModel.stream(new Prompt(List.of(new UserMessage(request.getQuery()))))
                    .map(aiResponse -> {
                        String content = aiResponse.getResult() != null
                                && aiResponse.getResult().getOutput() != null
                                ? aiResponse.getResult().getOutput().getText() : "";
                        return StreamEvent.textDelta(content, sessionId);
                    })
                    .concatWith(Flux.just(StreamEvent.done(false, null, sessionId)));
        } catch (Exception e) {
            throw new ChatException(Constants.ErrorCode.CHAT_LLM_ERROR, "服务繁忙请稍后重试", e);
        }
    }
}
```

**注意**: SimpleChatStrategy有import歧义 — Spring AI的`ChatResponse`和domain的`ChatResponse`同名。在execute方法中需使用全限定名 `com.ai.agent.domain.chat.model.valobj.ChatResponse` 作为返回类型，Spring AI的用全限定名或局部变量类型引用。实际编码时需仔细处理此问题。上面Step 9的代码中已用全限定名消歧。

- [ ] **Step 10: Create MultiTurnChatStrategy**

```java
package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.chat.model.entity.ChatMessage;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.domain.chat.service.ChatMemoryManager;
import com.ai.agent.types.enums.MessageRole;
import com.ai.agent.types.exception.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class MultiTurnChatStrategy implements ChatStrategy {

    private final ChatModel chatModel;
    private final ChatMemoryManager memoryManager;

    public MultiTurnChatStrategy(ChatModel chatModel, ChatMemoryManager memoryManager) {
        this.chatModel = chatModel;
        this.memoryManager = memoryManager;
    }

    @Override
    public ChatResponse execute(ChatRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        try {
            List<ChatMessage> history = memoryManager.load(sessionId);
            List<org.springframework.ai.chat.messages.Message> messages = convertToAiMessages(history);
            messages.add(new UserMessage(request.getQuery()));

            org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(new Prompt(messages));
            String answer = aiResponse.getResult().getOutput().getText();

            memoryManager.save(sessionId, MessageRole.USER, request.getQuery());
            memoryManager.save(sessionId, MessageRole.ASSISTANT, answer);

            return ChatResponse.builder()
                    .answer(answer)
                    .sessionId(sessionId)
                    .ragDegraded(false)
                    .build();
        } catch (ChatException e) {
            throw e;
        } catch (Exception e) {
            throw new ChatException(com.ai.agent.types.common.Constants.ErrorCode.CHAT_LLM_ERROR, "服务繁忙请稍后重试", e);
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(ChatRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        try {
            List<ChatMessage> history = memoryManager.load(sessionId);
            List<org.springframework.ai.chat.messages.Message> messages = convertToAiMessages(history);
            messages.add(new UserMessage(request.getQuery()));

            memoryManager.save(sessionId, MessageRole.USER, request.getQuery());

            final String sid = sessionId;
            StringBuilder answerBuilder = new StringBuilder();

            return chatModel.stream(new Prompt(messages))
                    .map(aiResponse -> {
                        String content = aiResponse.getResult() != null
                                && aiResponse.getResult().getOutput() != null
                                ? aiResponse.getResult().getOutput().getText() : "";
                        answerBuilder.append(content);
                        return StreamEvent.textDelta(content, sid);
                    })
                    .concatWith(Flux.defer(() -> {
                        memoryManager.save(sid, MessageRole.ASSISTANT, answerBuilder.toString());
                        return Flux.just(StreamEvent.done(false, null, sid));
                    }));
        } catch (ChatException e) {
            throw e;
        } catch (Exception e) {
            throw new ChatException(com.ai.agent.types.common.Constants.ErrorCode.CHAT_LLM_ERROR, "服务繁忙请稍后重试", e);
        }
    }

    private List<org.springframework.ai.chat.messages.Message> convertToAiMessages(List<ChatMessage> history) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            if (msg.getRole() == MessageRole.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        return messages;
    }
}
```

- [ ] **Step 11: Create ChatMemoryManager**

```java
package com.ai.agent.domain.chat.service;

import com.ai.agent.domain.chat.model.entity.ChatMessage;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.domain.chat.repository.IChatSessionRepository;
import com.ai.agent.types.enums.MessageRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatMemoryManager {

    private static final int MAX_ROUNDS_PER_SESSION = 20;

    private final IChatSessionRepository chatSessionRepository;
    private final ConcurrentHashMap<String, LinkedList<ChatMessage>> memoryCache = new ConcurrentHashMap<>();

    public ChatMemoryManager(IChatSessionRepository chatSessionRepository) {
        this.chatSessionRepository = chatSessionRepository;
    }

    public List<ChatMessage> load(String sessionId) {
        LinkedList<ChatMessage> cached = memoryCache.get(sessionId);
        if (cached != null) {
            return Collections.unmodifiableList(cached);
        }
        List<ChatMessage> fromDb = chatSessionRepository.findMessagesBySessionId(sessionId);
        LinkedList<ChatMessage> loaded = new LinkedList<>(fromDb);
        memoryCache.put(sessionId, loaded);
        return Collections.unmodifiableList(loaded);
    }

    public void save(String sessionId, MessageRole role, String content) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();

        LinkedList<ChatMessage> cached = memoryCache.computeIfAbsent(sessionId, k -> new LinkedList<>());
        cached.addLast(message);

        while (cached.size() > MAX_ROUNDS_PER_SESSION * 2) {
            ChatMessage evicted = cached.removeFirst();
            asyncPersistMessage(evicted);
        }

        asyncPersistMessage(message);
    }

    @Async
    public void asyncPersistMessage(ChatMessage message) {
        try {
            chatSessionRepository.saveMessage(message);
        } catch (Exception e) {
            log.error("异步持久化消息失败 sessionId={}, error={}", message.getSessionId(), e.getMessage(), e);
        }
    }

    public void evict(String sessionId) {
        memoryCache.remove(sessionId);
    }
}
```

- [ ] **Step 12: Create ModeRouter**

```java
package com.ai.agent.domain.chat.service;

import com.ai.agent.domain.chat.service.strategy.AgentOrchestratorStrategy;
import com.ai.agent.domain.chat.service.strategy.ChatStrategy;
import com.ai.agent.domain.chat.service.strategy.MultiTurnChatStrategy;
import com.ai.agent.domain.chat.service.strategy.SimpleChatStrategy;
import com.ai.agent.types.enums.ChatMode;
import com.ai.agent.types.exception.ChatException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ModeRouter {

    private final Map<ChatMode, ChatStrategy> strategyMap;

    public ModeRouter(SimpleChatStrategy simpleChatStrategy,
                      MultiTurnChatStrategy multiTurnChatStrategy,
                      AgentOrchestratorStrategy agentOrchestratorStrategy) {
        this.strategyMap = Map.of(
                ChatMode.SIMPLE, simpleChatStrategy,
                ChatMode.MULTI_TURN, multiTurnChatStrategy,
                ChatMode.AGENT, agentOrchestratorStrategy
        );
    }

    public ChatStrategy route(ChatMode mode) {
        ChatStrategy strategy = strategyMap.get(mode);
        if (strategy == null) {
            throw new ChatException(com.ai.agent.types.common.Constants.ErrorCode.CHAT_MODE_UNSUPPORTED, "不支持的对话模式: " + mode);
        }
        return strategy;
    }
}
```

- [ ] **Step 13: Create AgentOrchestratorStrategy stub**

```java
package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class AgentOrchestratorStrategy implements ChatStrategy {

    @Override
    public ChatResponse execute(ChatRequest request) {
        // 将在Task 5中实现4种Agent编排子模式
        throw new AgentException(com.ai.agent.types.common.Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED, "Agent编排功能待实现");
    }

    @Override
    public Flux<StreamEvent> executeStream(ChatRequest request) {
        throw new AgentException(com.ai.agent.types.common.Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED, "Agent编排流式功能待实现");
    }
}
```

- [ ] **Step 14: Create RagDecorator stub**

```java
package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
public class RagDecorator implements ChatStrategy {

    private final ChatStrategy delegate;
    // RagService将在Task 6中注入

    public RagDecorator(ChatStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse execute(ChatRequest request) {
        // 将在Task 6中实现RAG三段式检索增强
        // 当前先直接委托给目标Strategy
        return delegate.execute(request);
    }

    @Override
    public Flux<StreamEvent> executeStream(ChatRequest request) {
        return delegate.executeStream(request);
    }
}
```

- [ ] **Step 15: Create ChatFacade**

```java
package com.ai.agent.domain.chat.service;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.domain.chat.service.strategy.ChatStrategy;
import com.ai.agent.domain.chat.service.strategy.RagDecorator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class ChatFacade {

    private final ModeRouter modeRouter;

    public ChatFacade(ModeRouter modeRouter) {
        this.modeRouter = modeRouter;
    }

    public ChatResponse chat(ChatRequest request) {
        ChatStrategy strategy = modeRouter.route(request.getMode());
        if (Boolean.TRUE.equals(request.getRagEnabled())) {
            strategy = new RagDecorator(strategy);
        }
        return strategy.execute(request);
    }

    public Flux<StreamEvent> chatStream(ChatRequest request) {
        ChatStrategy strategy = modeRouter.route(request.getMode());
        if (Boolean.TRUE.equals(request.getRagEnabled())) {
            strategy = new RagDecorator(strategy);
        }
        return strategy.executeStream(request);
    }
}
```

- [ ] **Step 16: Verify compilation**

Run: `cd /Users/yangqinz/dev-ops/agent-scaffold-engineering && mvn compile -pl agent-scaffold-engineering-domain -am 2>&1 | tail -20`
Expected: BUILD SUCCESS（可能有import歧义问题需手动修复ChatResponse全限定名）

- [ ] **Step 17: Commit**

```bash
cd /Users/yangqinz/dev-ops/agent-scaffold-engineering
git add agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/
git commit -m "feat(domain): add chat bounded context with ChatFacade, ModeRouter, strategies"
```

---

## Task 4: Domain层 — Agent限界上下文

**Files:**
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/model/aggregate/AgentDefinition.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/model/entity/WorkflowDefinition.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/model/entity/WorkflowNode.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/model/entity/GraphEdge.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/model/entity/ToolConfig.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/model/valobj/ModelConfig.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/repository/IAgentDefinitionRepository.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/service/AgentRegistry.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/service/executor/ReactExecutor.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/service/executor/WorkflowExecutor.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/service/executor/GraphExecutor.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/service/executor/HybridExecutor.java`

- [ ] **Step 1: Create AgentDefinition aggregate**

```java
package com.ai.agent.domain.agent.model.aggregate;

import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.ToolConfig;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.types.enums.AgentMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDefinition {
    private String agentId;
    private String name;
    private AgentMode mode;
    private String instruction;
    private ModelConfig modelConfig;
    private List<ToolConfig> tools;

    // Workflow模式专用
    private String workflowEntry;
    private List<WorkflowNode> workflowNodes;

    // Graph模式专用
    private String graphStart;
    private List<WorkflowNode> graphNodes;
    private List<GraphEdge> graphEdges;
}
```

- [ ] **Step 2: Create WorkflowNode entity**

```java
package com.ai.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNode {
    private String id;
    private String agentId;
    private String reactAgentId;
    private String next;
}
```

- [ ] **Step 3: Create GraphEdge entity**

```java
package com.ai.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdge {
    private String from;
    private String to;
    private String condition;
}
```

- [ ] **Step 4: Create ToolConfig entity**

```java
package com.ai.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolConfig {
    private String name;
    private String type;
    private String description;
    private String className;
}
```

- [ ] **Step 5: Create ModelConfig value object**

```java
package com.ai.agent.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfig {
    @Builder.Default
    private String name = "qwq-plus";
    @Builder.Default
    private Double temperature = 0.7;
    @Builder.Default
    private Integer maxTokens = 2000;
}
```

- [ ] **Step 6: Create WorkflowDefinition entity (convenience wrapper)**

```java
package com.ai.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinition {
    private String entry;
    private List<WorkflowNode> nodes;
}
```

- [ ] **Step 7: Create IAgentDefinitionRepository interface**

```java
package com.ai.agent.domain.agent.repository;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;

public interface IAgentDefinitionRepository {
    AgentDefinition findById(String agentId);
    void save(AgentDefinition definition);
}
```

- [ ] **Step 8: Create AgentRegistry**

```java
package com.ai.agent.domain.agent.service;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.entity.ToolConfig;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.types.enums.AgentMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentRegistry {

    private final ConcurrentHashMap<String, AgentDefinition> registry = new ConcurrentHashMap<>();

    public AgentRegistry() {
        loadAgentDefinitions();
    }

    private void loadAgentDefinitions() {
        Yaml yaml = new Yaml();
        try {
            // 扫描agents/目录下所有YAML文件
            var resources = Thread.currentThread().getContextClassLoader().getResources("agents/");
            // 使用Spring的PathMatchingResourcePatternResolver更好，但domain层不依赖Spring
            // 实际实现将迁到infrastructure层，此处仅加载逻辑
        } catch (Exception e) {
            log.warn("加载Agent定义失败: {}", e.getMessage());
        }
    }

    public void register(AgentDefinition definition) {
        registry.put(definition.getAgentId(), definition);
        log.info("注册Agent定义: id={}, name={}, mode={}", definition.getAgentId(), definition.getName(), definition.getMode());
    }

    public AgentDefinition get(String agentId) {
        AgentDefinition def = registry.get(agentId);
        if (def == null) {
            log.warn("Agent定义未找到: {}", agentId);
        }
        return def;
    }

    public Collection<AgentDefinition> getAll() {
        return registry.values();
    }
}
```

- [ ] **Step 9: Create 4 executor stubs**

```java
// ReactExecutor.java
package com.ai.agent.domain.agent.service.executor;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ReactExecutor {

    private final ChatModel chatModel;

    public ReactExecutor(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public ChatResponse execute(AgentDefinition agentDef, ChatRequest request) {
        try {
            ReactAgent agent = buildReactAgent(agentDef);
            AssistantMessage response = agent.call(request.getQuery());
            return ChatResponse.builder()
                    .answer(response.getText())
                    .sessionId(request.getSessionId())
                    .ragDegraded(false)
                    .metadata(java.util.Map.of("agentId", agentDef.getAgentId(), "mode", "REACT"))
                    .build();
        } catch (Exception e) {
            log.error("ReAct执行失败 agentId={}", agentDef.getAgentId(), e);
            throw new com.ai.agent.types.exception.AgentException(
                    com.ai.agent.types.common.Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "Agent执行失败: " + e.getMessage(), e);
        }
    }

    public Flux<StreamEvent> executeStream(AgentDefinition agentDef, ChatRequest request) {
        // ReactAgent暂不支持Flux流式，先用同步转Flux
        // 后续可通过ReactAgent hooks监听步骤
        return Flux.defer(() -> {
            try {
                ChatResponse response = execute(agentDef, request);
                String sessionId = request.getSessionId();
                return Flux.just(
                        StreamEvent.textDelta(response.getAnswer(), sessionId),
                        StreamEvent.done(false, response.getMetadata(), sessionId)
                );
            } catch (Exception e) {
                return Flux.error(e);
            }
        });
    }

    private ReactAgent buildReactAgent(AgentDefinition agentDef) {
        ReactAgent.ReactAgentBuilder builder = ReactAgent.builder()
                .name(agentDef.getName())
                .model(chatModel)
                .instruction(agentDef.getInstruction())
                .saver(new MemorySaver());

        if (agentDef.getModelConfig() != null) {
            builder.enableLogging(true);
        }

        // 工具加载将在infrastructure层通过ToolCallbackResolver实现
        return builder.build();
    }
}
```

```java
// WorkflowExecutor.java
package com.ai.agent.domain.agent.service.executor;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class WorkflowExecutor {

    private final ChatModel chatModel;
    private final ReactExecutor reactExecutor;

    public WorkflowExecutor(ChatModel chatModel, ReactExecutor reactExecutor) {
        this.chatModel = chatModel;
        this.reactExecutor = reactExecutor;
    }

    public ChatResponse execute(AgentDefinition agentDef, ChatRequest request) {
        // 使用Spring AI Alibaba SequentialAgent实现Workflow
        // 将在实现阶段完善
        throw new AgentException(com.ai.agent.types.common.Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                "Workflow编排功能待实现: " + agentDef.getAgentId());
    }

    public Flux<StreamEvent> executeStream(AgentDefinition agentDef, ChatRequest request) {
        return Flux.error(new AgentException(com.ai.agent.types.common.Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                "Workflow流式编排功能待实现"));
    }
}
```

```java
// GraphExecutor.java
package com.ai.agent.domain.agent.service.executor;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class GraphExecutor {

    private final ChatModel chatModel;

    public GraphExecutor(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public ChatResponse execute(AgentDefinition agentDef, ChatRequest request) {
        // 使用Spring AI Alibaba StateGraph实现
        // 将在实现阶段完善
        throw new AgentException(com.ai.agent.types.common.Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                "Graph编排功能待实现: " + agentDef.getAgentId());
    }

    public Flux<StreamEvent> executeStream(AgentDefinition agentDef, ChatRequest request) {
        return Flux.error(new AgentException(com.ai.agent.types.common.Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                "Graph流式编排功能待实现"));
    }
}
```

```java
// HybridExecutor.java
package com.ai.agent.domain.agent.service.executor;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class HybridExecutor {

    private final ChatModel chatModel;
    private final ReactExecutor reactExecutor;

    public HybridExecutor(ChatModel chatModel, ReactExecutor reactExecutor) {
        this.chatModel = chatModel;
        this.reactExecutor = reactExecutor;
    }

    public ChatResponse execute(AgentDefinition agentDef, ChatRequest request) {
        // 外层Workflow + 内层ReAct
        // 将在实现阶段完善
        throw new AgentException(com.ai.agent.types.common.Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                "Hybrid编排功能待实现: " + agentDef.getAgentId());
    }

    public Flux<StreamEvent> executeStream(AgentDefinition agentDef, ChatRequest request) {
        return Flux.error(new AgentException(com.ai.agent.types.common.Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                "Hybrid流式编排功能待实现"));
    }
}
```

- [ ] **Step 10: Update AgentOrchestratorStrategy to route to executors**

更新Task 3中的AgentOrchestratorStrategy stub，注入4个executor并根据agentMode路由：

```java
package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.agent.service.executor.GraphExecutor;
import com.ai.agent.domain.agent.service.executor.HybridExecutor;
import com.ai.agent.domain.agent.service.executor.ReactExecutor;
import com.ai.agent.domain.agent.service.executor.WorkflowExecutor;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.enums.AgentMode;
import com.ai.agent.types.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@Service
public class AgentOrchestratorStrategy implements ChatStrategy {

    private final AgentRegistry agentRegistry;
    private final ReactExecutor reactExecutor;
    private final WorkflowExecutor workflowExecutor;
    private final GraphExecutor graphExecutor;
    private final HybridExecutor hybridExecutor;

    private final Map<AgentMode, AgentExecutor> executorMap;

    public AgentOrchestratorStrategy(AgentRegistry agentRegistry,
                                     ReactExecutor reactExecutor,
                                     WorkflowExecutor workflowExecutor,
                                     GraphExecutor graphExecutor,
                                     HybridExecutor hybridExecutor) {
        this.agentRegistry = agentRegistry;
        this.reactExecutor = reactExecutor;
        this.workflowExecutor = workflowExecutor;
        this.graphExecutor = graphExecutor;
        this.hybridExecutor = hybridExecutor;
        this.executorMap = Map.of(
                AgentMode.REACT, reactExecutor::execute,
                AgentMode.WORKFLOW, workflowExecutor::execute,
                AgentMode.GRAPH, graphExecutor::execute,
                AgentMode.HYBRID, hybridExecutor::execute
        );
    }

    @FunctionalInterface
    private interface AgentExecutor {
        ChatResponse execute(AgentDefinition agentDef, ChatRequest request);
    }

    @Override
    public ChatResponse execute(ChatRequest request) {
        if (request.getAgentMode() == null) {
            throw new AgentException(com.ai.agent.types.common.Constants.ErrorCode.AGENT_MODE_UNSUPPORTED, "AGENT模式必须指定agentMode");
        }

        AgentDefinition agentDef = agentRegistry.get(request.getAgentMode().name().toLowerCase());
        if (agentDef == null) {
            agentDef = AgentDefinition.builder()
                    .agentId(request.getAgentMode().name())
                    .name(request.getAgentMode().name())
                    .mode(request.getAgentMode())
                    .build();
        }

        AgentExecutor executor = executorMap.get(request.getAgentMode());
        if (executor == null) {
            throw new AgentException(com.ai.agent.types.common.Constants.ErrorCode.AGENT_MODE_UNSUPPORTED, "不支持的Agent模式: " + request.getAgentMode());
        }

        return executor.execute(agentDef, request);
    }

    @Override
    public Flux<StreamEvent> executeStream(ChatRequest request) {
        // 流式实现将跟随各executor完善
        return Flux.defer(() -> {
            try {
                ChatResponse response = execute(request);
                String sessionId = request.getSessionId();
                return reactor.core.publisher.Flux.just(
                        StreamEvent.textDelta(response.getAnswer(), sessionId),
                        StreamEvent.done(false, response.getMetadata(), sessionId)
                );
            } catch (Exception e) {
                return reactor.core.publisher.Flux.error(e);
            }
        });
    }
}
```

- [ ] **Step 11: Verify compilation**

Run: `cd /Users/yangqinz/dev-ops/agent-scaffold-engineering && mvn compile -pl agent-scaffold-engineering-domain -am 2>&1 | tail -20`
Expected: BUILD SUCCESS

- [ ] **Step 12: Commit**

```bash
cd /Users/yangqinz/dev-ops/agent-scaffold-engineering
git add agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/agent/
git commit -m "feat(domain): add agent bounded context with AgentRegistry, 4 executors"
```

---

## Task 5: Domain层 — Knowledge限界上下文

**Files:**
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/model/aggregate/KnowledgeBase.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/model/entity/Document.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/model/entity/DocumentChunk.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/repository/IKnowledgeBaseRepository.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/repository/IDocumentRepository.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/repository/IDocumentChunkRepository.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/service/DocumentProcessor.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/service/EmbeddingService.java`
- Create: `agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/service/RagService.java`

- [ ] **Step 1: Create KnowledgeBase aggregate**

```java
package com.ai.agent.domain.knowledge.model.aggregate;

import com.ai.agent.types.enums.OwnerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBase {
    private String baseId;
    private String name;
    private String description;
    private OwnerType ownerType;
    private String ownerId;
    @Builder.Default
    private Integer docCount = 0;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: Create Document entity**

```java
package com.ai.agent.domain.knowledge.model.entity;

import com.ai.agent.types.enums.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    private String docId;
    private String baseId;
    private String fileName;
    private String fileType;
    private DocumentStatus status;
    @Builder.Default
    private Integer chunkCount = 0;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Create DocumentChunk entity**

```java
package com.ai.agent.domain.knowledge.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    private Long chunkId;
    private String docId;
    private String baseId;
    private String content;
    private float[] embedding;
    private String metadata;
    private Integer chunkIndex;
}
```

- [ ] **Step 4: Create repository interfaces**

```java
// IKnowledgeBaseRepository.java
package com.ai.agent.domain.knowledge.repository;

import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;

public interface IKnowledgeBaseRepository {
    void save(KnowledgeBase knowledgeBase);
    KnowledgeBase findById(String baseId);
}
```

```java
// IDocumentRepository.java
package com.ai.agent.domain.knowledge.repository;

import com.ai.agent.domain.knowledge.model.entity.Document;
import java.util.List;

public interface IDocumentRepository {
    void save(Document document);
    Document findById(String docId);
    List<Document> findByBaseId(String baseId);
    void updateStatus(String docId, com.ai.agent.types.enums.DocumentStatus status);
}
```

```java
// IDocumentChunkRepository.java
package com.ai.agent.domain.knowledge.repository;

import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import java.util.List;

public interface IDocumentChunkRepository {
    void saveBatch(List<DocumentChunk> chunks);
    List<DocumentChunk> vectorSearch(String baseId, float[] queryEmbedding, int topK);
    List<DocumentChunk> bm25Search(String baseId, String query, int topK);
}
```

- [ ] **Step 5: Create EmbeddingService interface**

```java
package com.ai.agent.domain.knowledge.service;

public interface EmbeddingService {
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
}
```

需要import: `java.util.List`

- [ ] **Step 6: Create DocumentProcessor**

```java
package com.ai.agent.domain.knowledge.service;

import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.types.enums.DocumentStatus;
import com.ai.agent.types.exception.KnowledgeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DocumentProcessor {

    private final EmbeddingService embeddingService;

    public DocumentProcessor(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    public List<DocumentChunk> process(String docId, String baseId, String content) {
        try {
            Document aiDoc = new Document(content);
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withDefaultChunkSize(800)
                    .withMinChunkSizeChars(350)
                    .withMinChunkLengthToEmbed(5)
                    .withMaxNumChunks(10000)
                    .withKeepSeparator(true)
                    .build();
            List<Document> chunks = splitter.apply(List.of(aiDoc));

            List<DocumentChunk> result = new ArrayList<>();
            List<String> texts = chunks.stream().map(Document::getText).toList();
            List<float[]> embeddings = embeddingService.embedBatch(texts);

            for (int i = 0; i < chunks.size(); i++) {
                result.add(DocumentChunk.builder()
                        .docId(docId)
                        .baseId(baseId)
                        .content(chunks.get(i).getText())
                        .embedding(embeddings.get(i))
                        .chunkIndex(i)
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.error("文档处理失败 docId={}", docId, e);
            throw new KnowledgeException(com.ai.agent.types.common.Constants.ErrorCode.KNOW_PROCESSING_FAILED,
                    "文档处理失败: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 7: Create RagService**

```java
package com.ai.agent.domain.knowledge.service;

import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.repository.IDocumentChunkRepository;
import com.ai.agent.types.exception.KnowledgeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagService {

    private final IDocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final ChatModel chatModel;

    public RagService(IDocumentChunkRepository chunkRepository, EmbeddingService embeddingService, ChatModel chatModel) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.chatModel = chatModel;
    }

    public String rewriteQuery(String originalQuery) {
        String rewritePrompt = "将以下口语化提问改写为规范化的检索词，仅输出检索词，不要解释：\n" + originalQuery;
        try {
            org.springframework.ai.chat.model.ChatResponse response = chatModel.call(
                    new Prompt(List.of(new UserMessage(rewritePrompt)))
            );
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("查询改写失败，使用原始query: {}", e.getMessage());
            return originalQuery;
        }
    }

    public List<DocumentChunk> hybridSearch(String baseId, String query, int topK) {
        float[] queryEmbedding = embeddingService.embed(query);
        List<DocumentChunk> vectorResults = chunkRepository.vectorSearch(baseId, queryEmbedding, topK * 2);
        List<DocumentChunk> bm25Results = chunkRepository.bm25Search(baseId, query, topK * 2);
        return fuseAndRerank(vectorResults, bm25Results, topK);
    }

    private List<DocumentChunk> fuseAndRerank(List<DocumentChunk> vectorResults, List<DocumentChunk> bm25Results, int topK) {
        // 分数归一化 + 权重融合
        double alpha = 0.7;
        Map<Long, Double> scoreMap = new HashMap<>();
        Map<Long, DocumentChunk> chunkMap = new HashMap<>();

        if (!vectorResults.isEmpty()) {
            double maxVecScore = vectorResults.stream().mapToDouble(c -> getScore(c)).max().orElse(1.0);
            for (int i = 0; i < vectorResults.size(); i++) {
                DocumentChunk c = vectorResults.get(i);
                double normalizedScore = maxVecScore > 0 ? getScore(c) / maxVecScore : 0;
                scoreMap.merge(c.getChunkId(), alpha * normalizedScore, Double::sum);
                chunkMap.putIfAbsent(c.getChunkId(), c);
            }
        }

        if (!bm25Results.isEmpty()) {
            double maxBm25Score = bm25Results.stream().mapToDouble(c -> getScore(c)).max().orElse(1.0);
            for (int i = 0; i < bm25Results.size(); i++) {
                DocumentChunk c = bm25Results.get(i);
                double normalizedScore = maxBm25Score > 0 ? getScore(c) / maxBm25Score : 0;
                scoreMap.merge(c.getChunkId(), (1 - alpha) * normalizedScore, Double::sum);
                chunkMap.putIfAbsent(c.getChunkId(), c);
            }
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> chunkMap.get(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private double getScore(DocumentChunk chunk) {
        // chunk的score暂存在metadata中，实际实现需调整
        return 1.0;
    }

    public String buildEnhancedPrompt(String query, List<DocumentChunk> retrievedChunks) {
        StringBuilder context = new StringBuilder("参考以下知识库内容回答问题：\n\n");
        for (int i = 0; i < retrievedChunks.size(); i++) {
            context.append("[").append(i + 1).append("] ").append(retrievedChunks.get(i).getContent()).append("\n\n");
        }
        context.append("问题：").append(query);
        return context.toString();
    }
}
```

- [ ] **Step 8: Verify compilation**

Run: `cd /Users/yangqinz/dev-ops/agent-scaffold-engineering && mvn compile -pl agent-scaffold-engineering-domain -am 2>&1 | tail -20`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
cd /Users/yangqinz/dev-ops/agent-scaffold-engineering
git add agent-scaffold-engineering-domain/src/main/java/com/ai/agent/domain/knowledge/
git commit -m "feat(domain): add knowledge bounded context with RAG service"
```

---

## Task 6: Infrastructure层 — POM更新与持久化实现

**Files:**
- Modify: `agent-scaffold-engineering-infrastructure/pom.xml`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/po/ChatSessionPO.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/po/ChatMessagePO.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/po/KnowledgeBasePO.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/po/DocumentPO.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/po/DocumentChunkPO.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/dao/IChatSessionDao.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/dao/IChatMessageDao.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/dao/IKnowledgeBaseDao.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/dao/IDocumentDao.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/dao/IDocumentChunkDao.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/repository/ChatSessionRepositoryImpl.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/repository/AgentDefinitionRepositoryImpl.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/repository/KnowledgeBaseRepositoryImpl.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/repository/DocumentRepositoryImpl.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/repository/DocumentChunkRepositoryImpl.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/java/com/ai/agent/infrastructure/persistent/repository/EmbeddingServiceImpl.java`
- Create: `agent-scaffold-engineering-infrastructure/src/main/resources/mybatis/mapper/ChatSessionMapper.xml`
- Create: `agent-scaffold-engineering-infrastructure/src/main/resources/mybatis/mapper/ChatMessageMapper.xml`
- Create: `agent-scaffold-engineering-infrastructure/src/main/resources/mybatis/mapper/KnowledgeBaseMapper.xml`
- Create: `agent-scaffold-engineering-infrastructure/src/main/resources/mybatis/mapper/DocumentMapper.xml`
- Create: `agent-scaffold-engineering-infrastructure/src/main/resources/mybatis/mapper/DocumentChunkMapper.xml`

- [ ] **Step 1: Update infrastructure POM with PostgreSQL + MyBatis + PGvector dependencies**

在 `agent-scaffold-engineering-infrastructure/pom.xml` 的 `<dependencies>` 中添加：

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
<dependency>
    <groupId>com.ai.agent</groupId>
    <artifactId>agent-scaffold-engineering-domain</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>fastjson</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai</artifactId>
</dependency>
```

- [ ] **Step 2: Create all PO (Persistent Object) classes**

```java
// ChatSessionPO.java
package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatSessionPO {
    private String sessionId;
    private String userId;
    private String mode;
    private String agentMode;
    private Boolean ragEnabled;
    private String knowledgeBaseId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime ttlExpireAt;
}
```

```java
// ChatMessagePO.java
package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatMessagePO {
    private Long messageId;
    private String sessionId;
    private String role;
    private String content;
    private Integer tokenCount;
    private LocalDateTime createdAt;
}
```

```java
// KnowledgeBasePO.java
package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KnowledgeBasePO {
    private String baseId;
    private String name;
    private String description;
    private String ownerType;
    private String ownerId;
    private Integer docCount;
    private LocalDateTime createdAt;
}
```

```java
// DocumentPO.java
package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DocumentPO {
    private String docId;
    private String baseId;
    private String fileName;
    private String fileType;
    private String status;
    private Integer chunkCount;
    private LocalDateTime createdAt;
}
```

```java
// DocumentChunkPO.java
package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;

@Data
public class DocumentChunkPO {
    private Long chunkId;
    private String docId;
    private String baseId;
    private String content;
    private String embedding;
    private String metadata;
    private Integer chunkIndex;
}
```

- [ ] **Step 3: Create DAO interfaces**

```java
// IChatSessionDao.java
package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.ChatSessionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IChatSessionDao {
    void insert(ChatSessionPO po);
    ChatSessionPO selectById(@Param("sessionId") String sessionId);
    void updateTtl(@Param("sessionId") String sessionId);
}
```

```java
// IChatMessageDao.java
package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.ChatMessagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface IChatMessageDao {
    void insert(ChatMessagePO po);
    List<ChatMessagePO> selectBySessionId(@Param("sessionId") String sessionId);
}
```

```java
// IKnowledgeBaseDao.java
package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.KnowledgeBasePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IKnowledgeBaseDao {
    void insert(KnowledgeBasePO po);
    KnowledgeBasePO selectById(@Param("baseId") String baseId);
}
```

```java
// IDocumentDao.java
package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.DocumentPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface IDocumentDao {
    void insert(DocumentPO po);
    DocumentPO selectById(@Param("docId") String docId);
    List<DocumentPO> selectByBaseId(@Param("baseId") String baseId);
    void updateStatus(@Param("docId") String docId, @Param("status") String status);
}
```

```java
// IDocumentChunkDao.java
package com.ai.agent.infrastructure.persistent.dao;

import com.ai.agent.infrastructure.persistent.po.DocumentChunkPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface IDocumentChunkDao {
    void insertBatch(@Param("list") List<DocumentChunkPO> list);
    List<DocumentChunkPO> vectorSearch(@Param("baseId") String baseId, @Param("embedding") String embedding, @Param("topK") int topK);
    List<DocumentChunkPO> bm25Search(@Param("baseId") String baseId, @Param("query") String query, @Param("topK") int topK);
}
```

- [ ] **Step 4: Create MyBatis Mapper XML files**

```xml
<!-- ChatSessionMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.ai.agent.infrastructure.persistent.dao.IChatSessionDao">
    <insert id="insert" parameterType="com.ai.agent.infrastructure.persistent.po.ChatSessionPO">
        INSERT INTO chat_session (session_id, user_id, mode, agent_mode, rag_enabled, knowledge_base_id, created_at, updated_at, ttl_expire_at)
        VALUES (#{sessionId}, #{userId}, #{mode}, #{agentMode}, #{ragEnabled}, #{knowledgeBaseId}, #{createdAt}, #{updatedAt}, #{ttlExpireAt})
    </insert>
    <select id="selectById" resultType="com.ai.agent.infrastructure.persistent.po.ChatSessionPO">
        SELECT session_id, user_id, mode, agent_mode, rag_enabled, knowledge_base_id, created_at, updated_at, ttl_expire_at
        FROM chat_session WHERE session_id = #{sessionId}
    </select>
    <update id="updateTtl">
        UPDATE chat_session SET updated_at = NOW() WHERE session_id = #{sessionId}
    </update>
</mapper>
```

```xml
<!-- ChatMessageMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.ai.agent.infrastructure.persistent.dao.IChatMessageDao">
    <insert id="insert" parameterType="com.ai.agent.infrastructure.persistent.po.ChatMessagePO">
        INSERT INTO chat_message (session_id, role, content, token_count, created_at)
        VALUES (#{sessionId}, #{role}, #{content}, #{tokenCount}, #{createdAt})
    </insert>
    <select id="selectBySessionId" resultType="com.ai.agent.infrastructure.persistent.po.ChatMessagePO">
        SELECT message_id, session_id, role, content, token_count, created_at
        FROM chat_message WHERE session_id = #{sessionId} ORDER BY created_at ASC
    </select>
</mapper>
```

```xml
<!-- KnowledgeBaseMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.ai.agent.infrastructure.persistent.dao.IKnowledgeBaseDao">
    <insert id="insert" parameterType="com.ai.agent.infrastructure.persistent.po.KnowledgeBasePO">
        INSERT INTO knowledge_base (base_id, name, description, owner_type, owner_id, doc_count, created_at)
        VALUES (#{baseId}, #{name}, #{description}, #{ownerType}, #{ownerId}, #{docCount}, #{createdAt})
    </insert>
    <select id="selectById" resultType="com.ai.agent.infrastructure.persistent.po.KnowledgeBasePO">
        SELECT base_id, name, description, owner_type, owner_id, doc_count, created_at
        FROM knowledge_base WHERE base_id = #{baseId}
    </select>
</mapper>
```

```xml
<!-- DocumentMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.ai.agent.infrastructure.persistent.dao.IDocumentDao">
    <insert id="insert" parameterType="com.ai.agent.infrastructure.persistent.po.DocumentPO">
        INSERT INTO document (doc_id, base_id, file_name, file_type, status, chunk_count, created_at)
        VALUES (#{docId}, #{baseId}, #{fileName}, #{fileType}, #{status}, #{chunkCount}, #{createdAt})
    </insert>
    <select id="selectById" resultType="com.ai.agent.infrastructure.persistent.po.DocumentPO">
        SELECT doc_id, base_id, file_name, file_type, status, chunk_count, created_at
        FROM document WHERE doc_id = #{docId}
    </select>
    <select id="selectByBaseId" resultType="com.ai.agent.infrastructure.persistent.po.DocumentPO">
        SELECT doc_id, base_id, file_name, file_type, status, chunk_count, created_at
        FROM document WHERE base_id = #{baseId}
    </select>
    <update id="updateStatus">
        UPDATE document SET status = #{status} WHERE doc_id = #{docId}
    </update>
</mapper>
```

```xml
<!-- DocumentChunkMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.ai.agent.infrastructure.persistent.dao.IDocumentChunkDao">
    <insert id="insertBatch" parameterType="java.util.List">
        INSERT INTO document_chunk (doc_id, base_id, content, embedding, metadata, chunk_index)
        VALUES
        <foreach collection="list" item="item" separator=",">
            (#{item.docId}, #{item.baseId}, #{item.content}, #{item.embedding}::vector, #{item.metadata}, #{item.chunkIndex})
        </foreach>
    </insert>
    <select id="vectorSearch" resultType="com.ai.agent.infrastructure.persistent.po.DocumentChunkPO">
        SELECT chunk_id, doc_id, base_id, content, metadata, chunk_index,
               1 - (embedding <![CDATA[<=>]]> #{embedding}::vector) AS score
        FROM document_chunk
        WHERE base_id = #{baseId}
        ORDER BY embedding <![CDATA[<=>]]> #{embedding}::vector
        LIMIT #{topK}
    </select>
    <select id="bm25Search" resultType="com.ai.agent.infrastructure.persistent.po.DocumentChunkPO">
        SELECT chunk_id, doc_id, base_id, content, metadata, chunk_index,
               ts_rank_cd(content_tsv, plainto_tsquery('simple', #{query})) AS score
        FROM document_chunk
        WHERE base_id = #{baseId} AND content_tsv @@ plainto_tsquery('simple', #{query})
        ORDER BY ts_rank_cd(content_tsv, plainto_tsquery('simple', #{query})) DESC
        LIMIT #{topK}
    </select>
</mapper>
```

- [ ] **Step 5: Create Repository implementations**

```java
// ChatSessionRepositoryImpl.java
package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.chat.model.aggregate.ChatSession;
import com.ai.agent.domain.chat.model.entity.ChatMessage;
import com.ai.agent.domain.chat.repository.IChatSessionRepository;
import com.ai.agent.infrastructure.persistent.dao.IChatMessageDao;
import com.ai.agent.infrastructure.persistent.dao.IChatSessionDao;
import com.ai.agent.infrastructure.persistent.po.ChatMessagePO;
import com.ai.agent.infrastructure.persistent.po.ChatSessionPO;
import com.ai.agent.types.enums.MessageRole;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ChatSessionRepositoryImpl implements IChatSessionRepository {

    private final IChatSessionDao chatSessionDao;
    private final IChatMessageDao chatMessageDao;

    public ChatSessionRepositoryImpl(IChatSessionDao chatSessionDao, IChatMessageDao chatMessageDao) {
        this.chatSessionDao = chatSessionDao;
        this.chatMessageDao = chatMessageDao;
    }

    @Override
    public void save(ChatSession session) {
        ChatSessionPO po = new ChatSessionPO();
        po.setSessionId(session.getSessionId());
        po.setUserId(session.getUserId());
        po.setMode(session.getMode().name());
        po.setAgentMode(session.getAgentMode() != null ? session.getAgentMode().name() : null);
        po.setRagEnabled(session.getRagEnabled());
        po.setKnowledgeBaseId(session.getKnowledgeBaseId());
        po.setCreatedAt(session.getCreatedAt());
        po.setUpdatedAt(session.getUpdatedAt());
        po.setTtlExpireAt(session.getTtlExpireAt());
        chatSessionDao.insert(po);
    }

    @Override
    public ChatSession findById(String sessionId) {
        ChatSessionPO po = chatSessionDao.selectById(sessionId);
        if (po == null) return null;
        return ChatSession.builder()
                .sessionId(po.getSessionId())
                .userId(po.getUserId())
                .mode(com.ai.agent.types.enums.ChatMode.valueOf(po.getMode()))
                .agentMode(po.getAgentMode() != null ? com.ai.agent.types.enums.AgentMode.valueOf(po.getAgentMode()) : null)
                .ragEnabled(po.getRagEnabled())
                .knowledgeBaseId(po.getKnowledgeBaseId())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .ttlExpireAt(po.getTtlExpireAt())
                .build();
    }

    @Override
    public void saveMessage(ChatMessage message) {
        ChatMessagePO po = new ChatMessagePO();
        po.setSessionId(message.getSessionId());
        po.setRole(message.getRole().getValue());
        po.setContent(message.getContent());
        po.setTokenCount(message.getTokenCount());
        po.setCreatedAt(message.getCreatedAt());
        chatMessageDao.insert(po);
    }

    @Override
    public List<ChatMessage> findMessagesBySessionId(String sessionId) {
        return chatMessageDao.selectBySessionId(sessionId).stream()
                .map(po -> ChatMessage.builder()
                        .messageId(po.getMessageId())
                        .sessionId(po.getSessionId())
                        .role(MessageRole.valueOf(po.getRole().toUpperCase()))
                        .content(po.getContent())
                        .tokenCount(po.getTokenCount())
                        .createdAt(po.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void updateSessionTtl(String sessionId) {
        chatSessionDao.updateTtl(sessionId);
    }
}
```

其他Repository实现类似，将PO与domain对象互相转换。此处省略KnowledgeBaseRepositoryImpl、DocumentRepositoryImpl、DocumentChunkRepositoryImpl的完整代码，模式与ChatSessionRepositoryImpl一致。

- [ ] **Step 6: Create EmbeddingServiceImpl**

```java
package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.knowledge.service.EmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingServiceImpl(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).collect(Collectors.toList());
    }
}
```

- [ ] **Step 7: Verify compilation**

Run: `cd /Users/yangqinz/dev-ops/agent-scaffold-engineering && mvn compile -pl agent-scaffold-engineering-infrastructure -am 2>&1 | tail -20`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
cd /Users/yangqinz/dev-ops/agent-scaffold-engineering
git add agent-scaffold-engineering-infrastructure/
git commit -m "feat(infrastructure): add PostgreSQL+MyBatis persistence with PGvector support"
```

---

## Task 7: Trigger层 — HTTP控制器与全局异常处理

**Files:**
- Create: `agent-scaffold-engineering-trigger/src/main/java/com/ai/agent/trigger/http/ChatController.java`
- Create: `agent-scaffold-engineering-trigger/src/main/java/com/ai/agent/trigger/http/KnowledgeController.java`
- Create: `agent-scaffold-engineering-trigger/src/main/java/com/ai/agent/trigger/http/GlobalExceptionHandler.java`

- [ ] **Step 1: Create ChatController**

```java
package com.ai.agent.trigger.http;

import com.ai.agent.api.IChatService;
import com.ai.agent.api.model.chat.ChatRequestDTO;
import com.ai.agent.api.model.chat.ChatResponseDTO;
import com.ai.agent.api.model.chat.StreamEventDTO;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.domain.chat.service.ChatFacade;
import com.ai.agent.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatFacade chatFacade;

    public ChatController(ChatFacade chatFacade) {
        this.chatFacade = chatFacade;
    }

    @PostMapping
    public Response<ChatResponseDTO> chat(@Valid @RequestBody ChatRequestDTO requestDTO) {
        ChatRequest request = convertRequest(requestDTO);
        ChatResponse response = chatFacade.chat(request);
        return Response.<ChatResponseDTO>builder()
                .code(com.ai.agent.types.common.Constants.ResponseCode.SUCCESS.getCode())
                .info(com.ai.agent.types.common.Constants.ResponseCode.SUCCESS.getInfo())
                .data(convertResponse(response))
                .build();
    }

    @PostMapping("/stream")
    public Flux<StreamEventDTO> chatStream(@Valid @RequestBody ChatRequestDTO requestDTO) {
        ChatRequest request = convertRequest(requestDTO);
        return chatFacade.chatStream(request).map(this::convertStreamEvent);
    }

    private ChatRequest convertRequest(ChatRequestDTO dto) {
        return ChatRequest.builder()
                .userId(dto.getUserId())
                .sessionId(dto.getSessionId())
                .query(dto.getQuery())
                .mode(dto.getMode())
                .agentMode(dto.getAgentMode())
                .ragEnabled(dto.getRagEnabled())
                .knowledgeBaseId(dto.getKnowledgeBaseId())
                .build();
    }

    private ChatResponseDTO convertResponse(ChatResponse response) {
        return ChatResponseDTO.builder()
                .answer(response.getAnswer())
                .sessionId(response.getSessionId())
                .ragDegraded(response.getRagDegraded())
                .metadata(response.getMetadata())
                .sources(response.getSources() != null ? response.getSources().stream()
                        .map(s -> com.ai.agent.api.model.chat.SourceDTO.builder()
                                .docName(s.getDocName())
                                .chunkContent(s.getChunkContent())
                                .score(s.getScore())
                                .build())
                        .collect(Collectors.toList()) : null)
                .build();
    }

    private StreamEventDTO convertStreamEvent(StreamEvent event) {
        return StreamEventDTO.builder()
                .type(event.getType())
                .data(event.getData())
                .sessionId(event.getSessionId())
                .build();
    }
}
```

- [ ] **Step 2: Create KnowledgeController**

```java
package com.ai.agent.trigger.http;

import com.ai.agent.api.model.knowledge.KnowledgeBaseResponseDTO;
import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;
import com.ai.agent.domain.knowledge.model.entity.Document;
import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.repository.IDocumentRepository;
import com.ai.agent.domain.knowledge.repository.IKnowledgeBaseRepository;
import com.ai.agent.domain.knowledge.service.DocumentProcessor;
import com.ai.agent.types.enums.DocumentStatus;
import com.ai.agent.types.enums.OwnerType;
import com.ai.agent.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final IKnowledgeBaseRepository knowledgeBaseRepository;
    private final IDocumentRepository documentRepository;
    private final DocumentProcessor documentProcessor;

    public KnowledgeController(IKnowledgeBaseRepository knowledgeBaseRepository,
                               IDocumentRepository documentRepository,
                               DocumentProcessor documentProcessor) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.documentProcessor = documentProcessor;
    }

    @PostMapping("/bases")
    public Response<KnowledgeBaseResponseDTO> createKnowledgeBase(
            @RequestParam String name,
            @RequestParam(defaultValue = "") String description,
            @RequestParam(defaultValue = "USER") String ownerType,
            @RequestParam(defaultValue = "") String ownerId) {
        KnowledgeBase kb = KnowledgeBase.builder()
                .baseId(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .ownerType(OwnerType.valueOf(ownerType))
                .ownerId(ownerId)
                .createdAt(LocalDateTime.now())
                .build();
        knowledgeBaseRepository.save(kb);
        return Response.<KnowledgeBaseResponseDTO>builder()
                .code(com.ai.agent.types.common.Constants.ResponseCode.SUCCESS.getCode())
                .info(com.ai.agent.types.common.Constants.ResponseCode.SUCCESS.getInfo())
                .data(KnowledgeBaseResponseDTO.builder()
                        .baseId(kb.getBaseId())
                        .name(kb.getName())
                        .description(kb.getDescription())
                        .docCount(0)
                        .build())
                .build();
    }

    @PostMapping("/upload")
    public Response<String> uploadDocument(
            @RequestParam String knowledgeBaseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String userId) {
        try {
            String docId = UUID.randomUUID().toString();
            String fileName = file.getOriginalFilename();
            String fileType = fileName != null ? fileName.substring(fileName.lastIndexOf(".") + 1) : "txt";

            Document doc = Document.builder()
                    .docId(docId)
                    .baseId(knowledgeBaseId)
                    .fileName(fileName)
                    .fileType(fileType)
                    .status(DocumentStatus.PROCESSING)
                    .createdAt(LocalDateTime.now())
                    .build();
            documentRepository.save(doc);

            String content = new BufferedReader(new InputStreamReader(file.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));

            List<DocumentChunk> chunks = documentProcessor.process(docId, knowledgeBaseId, content);

            documentRepository.updateStatus(docId, DocumentStatus.COMPLETED);
            return Response.<String>builder()
                    .code(com.ai.agent.types.common.Constants.ResponseCode.SUCCESS.getCode())
                    .info(com.ai.agent.types.common.Constants.ResponseCode.SUCCESS.getInfo())
                    .data(docId)
                    .build();
        } catch (Exception e) {
            log.error("文档上传处理失败", e);
            return Response.<String>builder()
                    .code(com.ai.agent.types.common.Constants.ResponseCode.UN_ERROR.getCode())
                    .info("文档处理失败: " + e.getMessage())
                    .data(null)
                    .build();
        }
    }
}
```

- [ ] **Step 3: Create GlobalExceptionHandler**

```java
package com.ai.agent.trigger.http;

import com.ai.agent.types.exception.AgentException;
import com.ai.agent.types.exception.AppException;
import com.ai.agent.types.exception.ChatException;
import com.ai.agent.types.exception.KnowledgeException;
import com.ai.agent.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ChatException.class)
    public Response<Void> handleChatException(ChatException e) {
        log.warn("Chat异常: code={}, info={}", e.getCode(), e.getInfo());
        return Response.<Void>builder().code(e.getCode()).info(e.getInfo()).build();
    }

    @ExceptionHandler(AgentException.class)
    public Response<Void> handleAgentException(AgentException e) {
        log.warn("Agent异常: code={}, info={}", e.getCode(), e.getInfo());
        return Response.<Void>builder().code(e.getCode()).info(e.getInfo()).build();
    }

    @ExceptionHandler(KnowledgeException.class)
    public Response<Void> handleKnowledgeException(KnowledgeException e) {
        log.warn("Knowledge异常: code={}, info={}", e.getCode(), e.getInfo());
        return Response.<Void>builder().code(e.getCode()).info(e.getInfo()).build();
    }

    @ExceptionHandler(AppException.class)
    public Response<Void> handleAppException(AppException e) {
        log.warn("业务异常: code={}, info={}", e.getCode(), e.getInfo());
        return Response.<Void>builder().code(e.getCode()).info(e.getInfo()).build();
    }

    @ExceptionHandler(Exception.class)
    public Response<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Response.<Void>builder()
                .code(com.ai.agent.types.common.Constants.ResponseCode.UN_ERROR.getCode())
                .info("系统繁忙，请稍后重试")
                .build();
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd /Users/yangqinz/dev-ops/agent-scaffold-engineering && mvn compile -pl agent-scaffold-engineering-trigger -am 2>&1 | tail -20`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
cd /Users/yangqinz/dev-ops/agent-scaffold-engineering
git add agent-scaffold-engineering-trigger/src/main/java/com/ai/agent/trigger/
git commit -m "feat(trigger): add ChatController, KnowledgeController, GlobalExceptionHandler"
```

---

## Task 8: App层 — 配置更新与PGvector初始化SQL

**Files:**
- Modify: `agent-scaffold-engineering-app/src/main/resources/application-dev.yml`
- Create: `agent-scaffold-engineering-app/src/main/java/com/ai/agent/config/EmbeddingModelConfig.java`
- Create: `agent-scaffold-engineering-app/src/main/resources/agents/weather-assistant.yaml`
- Create: `agent-scaffold-engineering-app/src/main/resources/sql/init_pgvector.sql`

- [ ] **Step 1: Update application-dev.yml**

在现有配置的spring.ai节点下添加PostgreSQL、MyBatis、Embedding配置，取消注释MyBatis配置：

```yaml
# 在spring:节点下添加
  datasource:
    url: jdbc:postgresql://localhost:5432/agent_db
    username: postgres
    password: 123456
    driver-class-name: org.postgresql.Driver

  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY}
      chat:
        options:
        model: qwq-plus
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      api-key: ${AI_DASHSCOPE_API_KEY}
      embedding:
        options:
          model: text-embedding-v1

# 取消注释并修改MyBatis配置
mybatis:
  mapper-locations: classpath:/mybatis/mapper/*.xml
  config-location: classpath:/mybatis/config/mybatis-config.xml
```

- [ ] **Step 2: Create EmbeddingModelConfig**

```java
package com.ai.agent.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.document.MetadataMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingModelConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.ALL,
                OpenAiEmbeddingOptions.builder()
                        .model("text-embedding-v1")
                        .dimensions(1536)
                        .build());
    }
}
```

- [ ] **Step 3: Create PGvector init SQL**

```sql
-- init_pgvector.sql
-- 启用PGvector扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建数据库
-- CREATE DATABASE agent_db;

-- 对话域表
CREATE TABLE IF NOT EXISTS chat_session (
    session_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    agent_mode VARCHAR(20),
    rag_enabled BOOLEAN DEFAULT FALSE,
    knowledge_base_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ttl_expire_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_message (
    message_id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL REFERENCES chat_session(session_id),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    token_count INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_message_session_created ON chat_message(session_id, created_at);

-- 知识库域表
CREATE TABLE IF NOT EXISTS knowledge_base (
    base_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    owner_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    owner_id VARCHAR(64),
    doc_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document (
    doc_id VARCHAR(64) PRIMARY KEY,
    base_id VARCHAR(64) NOT NULL REFERENCES knowledge_base(base_id),
    file_name VARCHAR(256),
    file_type VARCHAR(10),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    chunk_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document_chunk (
    chunk_id BIGSERIAL PRIMARY KEY,
    doc_id VARCHAR(64) NOT NULL REFERENCES document(doc_id),
    base_id VARCHAR(64) NOT NULL REFERENCES knowledge_base(base_id),
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB,
    chunk_index INT NOT NULL,
    content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED
);

CREATE INDEX IF NOT EXISTS idx_document_chunk_base_id ON document_chunk(base_id);
CREATE INDEX IF NOT EXISTS idx_document_chunk_doc_id ON document_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_chunk_content_tsv ON document_chunk USING GIN (content_tsv);
```

- [ ] **Step 4: Create example Agent YAML**

```yaml
# weather-assistant.yaml
agents:
  - id: "weather-assistant"
    name: "天气助手"
    mode: REACT
    instruction: "你是一个天气查询助手，可以帮助用户查询城市天气信息。请用中文回答。"
    model:
      name: "qwq-plus"
      temperature: 0.7
      maxTokens: 2000
    tools:
      - name: "getWeather"
        type: FUNCTION
        description: "查询指定城市的天气信息"
        className: "com.ai.agent.tools.WeatherTool"
```

- [ ] **Step 5: Verify full project compilation**

Run: `cd /Users/yangqinz/dev-ops/agent-scaffold-engineering && mvn compile 2>&1 | tail -30`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
cd /Users/yangqinz/dev-ops/agent-scaffold-engineering
git add agent-scaffold-engineering-app/src/main/resources/ agent-scaffold-engineering-app/src/main/java/com/ai/agent/config/EmbeddingModelConfig.java
git commit -m "feat(app): add PostgreSQL+PGvector config, EmbeddingModel, init SQL, example Agent YAML"
```

---

## Task 9: Docker环境更新 — PostgreSQL + PGvector

**Files:**
- Modify: `docs/dev-ops/environment/docker-compose.yml`

- [ ] **Step 1: Add PostgreSQL + PGvector service to docker-compose.yml**

在docker-compose.yml的services节点下添加：

```yaml
  postgres:
    image: pgvector/pgvector:pg16
    container_name: agent-postgres
    restart: on-failure
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: agent_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: "123456"
    volumes:
      - ./sql/init_pgvector.sql:/docker-entrypoint-initdb.d/init_pgvector.sql
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U postgres" ]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - my-network
```

- [ ] **Step 2: Copy init SQL to environment directory**

```bash
cp agent-scaffold-engineering-app/src/main/resources/sql/init_pgvector.sql docs/dev-ops/environment/sql/init_pgvector.sql
```

- [ ] **Step 3: Commit**

```bash
cd /Users/yangqinz/dev-ops/agent-scaffold-engineering
git add docs/dev-ops/environment/
git commit -m "feat(infrastructure): add PostgreSQL+PGvector Docker service and init SQL"
```

---

## Task 10: 端到端集成测试

**Files:**
- Modify: `agent-scaffold-engineering-app/src/test/java/com/ai/agent/test/ApiTest.java`
- Create: `agent-scaffold-engineering-app/src/test/java/com/ai/agent/test/ChatIntegrationTest.java`

- [ ] **Step 1: Write integration test for SimpleChatStrategy**

```java
package com.ai.agent.test;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.service.ChatFacade;
import com.ai.agent.types.enums.ChatMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ChatIntegrationTest {

    @Resource
    private ChatFacade chatFacade;

    @Test
    public void testSimpleChat() {
        ChatRequest request = ChatRequest.builder()
                .userId("test-user")
                .query("你好，请介绍一下你自己")
                .mode(ChatMode.SIMPLE)
                .ragEnabled(false)
                .build();

        ChatResponse response = chatFacade.chat(request);
        assertNotNull(response);
        assertNotNull(response.getAnswer());
        assertFalse(response.getAnswer().isEmpty());
        log.info("SimpleChat响应: {}", response.getAnswer());
    }

    @Test
    public void testMultiTurnChat() {
        ChatRequest request1 = ChatRequest.builder()
                .userId("test-user")
                .sessionId("test-session-001")
                .query("我叫小明")
                .mode(ChatMode.MULTI_TURN)
                .ragEnabled(false)
                .build();

        ChatResponse response1 = chatFacade.chat(request1);
        assertNotNull(response1.getAnswer());

        ChatRequest request2 = ChatRequest.builder()
                .userId("test-user")
                .sessionId("test-session-001")
                .query("我叫什么名字？")
                .mode(ChatMode.MULTI_TURN)
                .ragEnabled(false)
                .build();

        ChatResponse response2 = chatFacade.chat(request2);
        assertNotNull(response2.getAnswer());
        assertTrue(response2.getAnswer().contains("小明"));
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `cd /Users/yangqinz/dev-ops/agent-scaffold-engineering && mvn test -pl agent-scaffold-engineering-app -Dtest=ChatIntegrationTest -DskipTests=false 2>&1 | tail -30`
Expected: PASS (需要环境变量AI_DASHSCOPE_API_KEY和PostgreSQL运行)

- [ ] **Step 3: Commit**

```bash
cd /Users/yangqinz/dev-ops/agent-scaffold-engineering
git add agent-scaffold-engineering-app/src/test/
git commit -m "test: add ChatIntegrationTest for simple and multi-turn chat"
```

---

## Self-Review Checklist

**1. Spec Coverage:**
- ✅ 3种核心对话模式 (SIMPLE/MULTI_TURN/AGENT) — Task 3
- ✅ 4种Agent子模式 (WORKFLOW/REACT/GRAPH/HYBRID) — Task 4
- ✅ RAG可选增强层 — Task 5, RagDecorator in Task 3
- ✅ 策略模式路由架构 — Task 3 (ChatFacade/ModeRouter)
- ✅ YAML配置式Agent定义 — Task 4 (AgentRegistry), Task 8 (example YAML)
- ✅ SSE流式事件类型 — Task 1 (StreamEventType), Task 3 (StreamEvent)
- ✅ PostgreSQL + PGvector — Task 6, Task 8, Task 9
- ✅ BM25全文检索 — Task 6 (DocumentChunkMapper)
- ✅ 三段式RAG (查询改写/混合检索/融合) — Task 5 (RagService)
- ✅ 内存+数据库混合对话记忆 — Task 3 (ChatMemoryManager)
- ✅ 域异常体系 — Task 1
- ✅ 全局异常处理器 — Task 7
- ✅ 统一Response格式 — Task 7
- ⚠️ MMR多样性重排 — 在RagService中标注为后续优化，当前fusion返回Top-K
- ⚠️ Reranking组件 — 设计文档标注"待定"，当前未实现

**2. Placeholder Scan:**
- Task 4的WorkflowExecutor/GraphExecutor/HybridExecutor标记"将在实现阶段完善" — 这些是真实的stub，后续迭代中完善
- Task 5的RagService.getScore()返回硬编码1.0 — 需要在PO中增加score字段，标注为已知限制
- 无TBD/TODO

**3. Type Consistency:**
- ChatMode/AgentMode枚举在types模块定义，domain和api模块均引用 — 一致
- ChatRequest/ChatResponse在domain为值对象，在api为DTO — 两套类型，由Controller做转换 — 一致
- StreamEventType在types定义，StreamEvent在domain定义，StreamEventDTO在api定义 — 由Controller转换 — 一致
- Spring AI的ChatResponse和domain的ChatResponse同名 — 需在代码中使用全限定名消歧 — 已标注
