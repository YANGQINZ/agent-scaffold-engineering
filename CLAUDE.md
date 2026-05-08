# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Full build (skipTests=true by default in root POM)
mvn clean install

# Run a single test
mvn test -pl agent-scaffold-engineering-app -Dtest=ApiTest#test -DskipTests=false

# Run application (requires AI_DASHSCOPE_API_KEY)
AI_DASHSCOPE_API_KEY=your-key mvn spring-boot:run -pl agent-scaffold-engineering-app

# Override model (default is qwen-plus)
AI_DASHSCOPE_API_KEY=your-key AI_MODEL=qwq-plus mvn spring-boot:run -pl agent-scaffold-engineering-app
```

Port `8091`, profile `dev`. PostgreSQL (pgvector) at `localhost:15432/agent_db` (overridable via `POSTGRES_HOST`/`POSTGRES_PORT`/`POSTGRES_DB` env vars). Redis at `127.0.0.1:16379`.

Dev environment: `docs/dev-ops/environment/docker-compose.yml` (PostgreSQL, MySQL, Redis, Nacos, RocketMQ, XXL-Job).

## Architecture — DDD Multi-Module

```
types       — enums, constants, exceptions, Response<T> (no dependencies)
  ↑
api         — service interfaces (IChatService, IKnowledgeBaseService) + DTOs
  ↑
domain      — core logic, zero infrastructure deps
  ↑          ↑
trigger     infrastructure
(HTTP       (Repository impls, MyBatis mappers, PO classes,
 controllers) Redis clients, external API clients)
  ↑          ↑
      app (Bootstrap, Spring config, tests)
```

## Domain: Four Bounded Contexts

### chat (`domain.chat`)
ChatFacade → ModeRouter → ChatStrategy (strategy pattern). Three modes via `ChatMode` enum:
- `SIMPLE` → SimpleChatStrategy (stateless, single-turn)
- `MULTI_TURN` → MultiTurnChatStrategy (session memory via ContextStore)
- `AGENT` → TaskRuntime (delegates to agent engine)

**RagDecorator** wraps any ChatStrategy when `ragEnabled=true`; gracefully degrades on failure.

### agent (`domain.agent`)
AgentRegistry (`ConcurrentHashMap`) holds AgentDefinition aggregates loaded from YAML at startup by `AgentYamlLoader`. TaskRuntime routes to `EngineAdapter` implementations via `EngineType` enum:
- `CHAT` → direct ChatModel calls
- `GRAPH` → GraphEngineAdapter (Spring AI Alibaba StateGraph, DAG with conditional edges)
- `AGENTSCOPE` → AgentScopeAdapter (spring-ai-alibaba SequentialAgent, multi-agent sequential pipelines)
- `HYBRID` → HybridEngineAdapter (outer StateGraph + per-node engine dispatch)

YAML agent definitions live in `agent-scaffold-engineering-app/src/main/resources/agents/` (chat/, graph/, agentscope/, hybrid/). AgentDefinition has four subclasses: `ChatAgentDefinition`, `GraphAgentDefinition`, `AgentscopeAgentDefinition`, `HybridAgentDefinition`.

### knowledge (`domain.knowledge`)
RAG pipeline in RagService: query rewrite (LLM) → hybrid search (vector α=0.7 + BM25 β=0.3) → RRF fusion (K=60) → reranking (qwen3-rerank) + MMR diversity → enhanced prompt construction.

### memory (`domain.memory`)
MemoryFacade (three-tier): cold layer (PostgreSQL chat messages), hot layer (Redis recent context cache), processing (async MemoryExtractor + ContextCompressor). ContextStoreFactory creates SessionContext instances with 30-min TTL and scheduled eviction.

## Key Design Patterns

- **Strategy + Router**: ChatStrategy / EngineAdapter interfaces with enum-keyed dispatch (no if-else routing).
- **Decorator**: RagDecorator wraps any ChatStrategy to inject RAG.
- **Registry**: AgentRegistry loaded from YAML, validated for cross-references at startup.
- **Facade**: ChatFacade (unified chat entry), MemoryFacade (unified memory entry).
- **Repository interface in domain, impl in infrastructure**: e.g. IChatSessionRepository → ChatSessionRepositoryImpl.

### Adding a new domain context

Copy an existing context directory under `domain/src/main/java/com/ai/agent/domain/` and rename the package. Each context must contain: `model/aggregate/`, `model/entity/`, `model/valobj/`, `repository/` (interfaces only), `service/`.

## Spring AI Integration

- Spring AI BOM 1.1.4 + Spring AI Alibaba BOM 1.1.2.0 + Extensions BOM 1.1.2.1
- Default model: `qwen-plus` via DashScope (configurable via `AI_MODEL` env var). Embedding: `text-embedding-v3` (1536 dims).
- `spring-ai-starter-mcp-client-webflux` enables MCP client (stdio/SSE/streamable-HTTP transports).
- `spring-ai-alibaba-agent-framework` provides StateGraph for GraphEngineAdapter.
- `spring-ai-agent-utils` used in domain layer.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/chat` | Sync chat |
| POST | `/api/v1/chat/stream` | Streaming chat (Flux SSE) |
| DELETE | `/api/v1/chat/session/{sessionId}` | Delete session |
| POST | `/api/v1/knowledge/upload` | Upload knowledge document (multipart) |

## Commented-Out Infrastructure

Redis, RocketMQ, XXL-Job configs exist in YAML and code but are commented out. Uncomment when needed. Docker Compose at `docs/dev-ops/environment/docker-compose.yml` sets up all these services plus pgvector PostgreSQL.

## Conventions

- Code comments, log messages, exception messages in Chinese.
- Lombok throughout (`@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor`, `@SuperBuilder` for aggregate inheritance).
- PO classes in infrastructure map to domain entities for persistence.
- MyBatis mapper XMLs in `agent-scaffold-engineering-app/src/main/resources/mybatis/mapper/`.
- Tests use JUnit 4 with `@RunWith(SpringRunner.class)` + `@SpringBootTest`.
- Domain layer has zero infrastructure dependencies — all external access through repository interfaces.
- Cross-context calls must go through Facade or Service interfaces, never direct internal class references.
