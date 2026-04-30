package com.ai.agent.trigger.http;

import com.ai.agent.api.IChatService;
import com.ai.agent.api.model.chat.ChatRequestDTO;
import com.ai.agent.api.model.chat.ChatResponseDTO;
import com.ai.agent.api.model.chat.SourceDTO;
import com.ai.agent.api.model.chat.StreamEventDTO;
import com.ai.agent.domain.common.valobj.ChatRequest;
import com.ai.agent.domain.common.valobj.ChatResponse;
import com.ai.agent.domain.common.valobj.StreamEvent;
import com.ai.agent.domain.chat.service.ChatFacade;
import com.ai.agent.trigger.converter.AgentDefinitionConverter;
import com.ai.agent.types.model.Response;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController implements IChatService {

    private final ChatFacade chatFacade;

    public ChatController(ChatFacade chatFacade) {
        this.chatFacade = chatFacade;
    }

    @PostMapping
    public Response<ChatResponseDTO> chat(@Valid @RequestBody ChatRequestDTO requestDTO) {
        ChatRequest request = convertRequest(requestDTO);
        ChatResponse response = chatFacade.chat(request);
        return Response.buildSuccess(convertResponse(response));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamEventDTO> chatStream(@Valid @RequestBody ChatRequestDTO requestDTO) {
        ChatRequest request = convertRequest(requestDTO);
        return chatFacade.chatStream(request).map(this::convertStreamEvent);
    }

    /**
     * 删除会话上下文 — 会话清理
     */
    @DeleteMapping("/session/{sessionId}")
    public Response<Void> removeSession(@PathVariable String sessionId) {
        chatFacade.removeSession(sessionId);
        return Response.buildSuccess();
    }

    private ChatRequest convertRequest(ChatRequestDTO dto) {
        return ChatRequest.builder()
                .userId(dto.getUserId())
                .sessionId(dto.getSessionId())
                .query(dto.getQuery())
                .mode(dto.getMode())
                .engine(dto.getEngine())
                .ragEnabled(dto.getRagEnabled())
                .knowledgeBaseId(dto.getKnowledgeBaseId())
                .enableThinking(dto.getEnableThinking())
                .agentId(dto.getAgentId())
                .agentDefinition(dto.getAgentDefinition() != null
                        ? AgentDefinitionConverter.toDomain(dto.getAgentDefinition()) : null)
                .build();
    }

    private ChatResponseDTO convertResponse(ChatResponse response) {
        return ChatResponseDTO.builder()
                .answer(response.getAnswer())
                .sessionId(response.getSessionId())
                .ragDegraded(response.getRagDegraded())
                .thinkingContent(response.getThinkingContent())
                .metadata(response.getMetadata())
                .sources(response.getSources() != null ? response.getSources().stream()
                        .map(s -> SourceDTO.builder().docName(s.getDocName()).chunkContent(s.getChunkContent()).score(s.getScore()).build())
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
