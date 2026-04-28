package com.ai.agent.api;

import com.ai.agent.api.model.chat.ChatRequestDTO;
import com.ai.agent.api.model.chat.ChatResponseDTO;
import com.ai.agent.api.model.chat.StreamEventDTO;
import com.ai.agent.types.model.Response;
import reactor.core.publisher.Flux;

public interface IChatService {
    Response<ChatResponseDTO> chat(ChatRequestDTO request);
    Flux<StreamEventDTO> chatStream(ChatRequestDTO request);
    Response<Void> removeSession(String sessionId);
}
