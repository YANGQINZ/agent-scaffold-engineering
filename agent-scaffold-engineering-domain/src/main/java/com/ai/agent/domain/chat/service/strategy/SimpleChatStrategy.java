package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.exception.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
public class SimpleChatStrategy implements ChatStrategy {

    private final ChatModel chatModel;

    public SimpleChatStrategy(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public ChatResponse execute(ChatRequest request) {
        try {
            org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(
                    new Prompt(List.of(new UserMessage(request.getQuery())))
            );
            String answer = aiResponse.getResult().getOutput().getText();
            return ChatResponse.builder()
                    .answer(answer)
                    .sessionId(request.getSessionId())
                    .ragDegraded(false)
                    .build();
        } catch (Exception e) {
            log.error("SimpleChat LLM call failed: {}", e.getMessage(), e);
            throw new ChatException(Constants.ErrorCode.CHAT_LLM_ERROR, "服务繁忙请稍后重试", e);
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(ChatRequest request) {
        String sessionId = request.getSessionId();
        return chatModel.stream(new Prompt(List.of(new UserMessage(request.getQuery()))))
                .map(aiResponse -> {
                    String content = aiResponse.getResult() != null && aiResponse.getResult().getOutput() != null
                            ? aiResponse.getResult().getOutput().getText() : "";
                    return StreamEvent.textDelta(content, sessionId);
                })
                .concatWith(Flux.just(StreamEvent.done(false, null, sessionId)));
    }

}
