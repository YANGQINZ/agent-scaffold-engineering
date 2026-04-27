package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.domain.chat.model.valobj.ThinkingExtractor;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.exception.ChatException;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
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
            Prompt prompt = buildPrompt(request);
            org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(prompt);

            ThinkingExtractor.ThinkingResult result = ThinkingExtractor.extractFromSpringAi(aiResponse);

            return ChatResponse.builder()
                    .answer(result.textContent())
                    .thinkingContent(result.hasThinking() ? result.thinkingContent() : null)
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
        Prompt prompt = buildPrompt(request);

        return chatModel.stream(prompt)
                .flatMap(aiResponse -> {
                    ThinkingExtractor.ThinkingResult result = ThinkingExtractor.extractFromSpringAi(aiResponse);
                    Flux<StreamEvent> events = Flux.empty();

                    if (result.hasThinking()) {
                        events = events.concatWith(Flux.just(StreamEvent.thinking(result.thinkingContent(), sessionId)));
                    }
                    if (!result.textContent().isEmpty()) {
                        events = events.concatWith(Flux.just(StreamEvent.textDelta(result.textContent(), sessionId)));
                    }

                    return events;
                })
                .concatWith(Flux.just(StreamEvent.done(false, null, sessionId)));
    }

    /**
     * 构建 Prompt — enableThinking=true 时注入 DashScopeChatOptions
     */
    private Prompt buildPrompt(ChatRequest request) {
        List<org.springframework.ai.chat.messages.Message> messages =
                List.of(new UserMessage(request.getQuery()));

        if (Boolean.TRUE.equals(request.getEnableThinking())) {
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .withEnableThinking(true)
                    .build();
            return new Prompt(messages, options);
        }

        return new Prompt(messages);
    }

}
