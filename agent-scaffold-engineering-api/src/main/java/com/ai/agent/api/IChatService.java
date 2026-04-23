package com.ai.agent.api;

import com.ai.agent.api.model.chat.ChatRequestDTO;
import com.ai.agent.api.model.chat.ChatResponseDTO;
import com.ai.agent.api.model.chat.StreamEventDTO;
import reactor.core.publisher.Flux;

public interface IChatService {
    ChatResponseDTO chat(ChatRequestDTO request);
    Flux<StreamEventDTO> chatStream(ChatRequestDTO request);
}
