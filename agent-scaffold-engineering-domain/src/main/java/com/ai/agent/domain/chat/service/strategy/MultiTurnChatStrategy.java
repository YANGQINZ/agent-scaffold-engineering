package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.agent.repository.ContextStore;
import com.ai.agent.domain.agent.service.ContextStoreFactory;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.domain.chat.model.valobj.ThinkingExtractor;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.exception.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class MultiTurnChatStrategy implements ChatStrategy {

    private final ChatModel chatModel;
    private final ContextStoreFactory contextStoreFactory;

    public MultiTurnChatStrategy(ChatModel chatModel, ContextStoreFactory contextStoreFactory) {
        this.chatModel = chatModel;
        this.contextStoreFactory = contextStoreFactory;
    }

    @Override
    public ChatResponse execute(ChatRequest request) {
        String sessionId = ensureSessionId(request);

        try {
            ContextStore ctx = contextStoreFactory.getOrCreate(
                    sessionId, request.getUserId(), EngineType.GRAPH, false, null);

            // 通过 ContextStore 组装记忆上下文
            String memoryContext = ctx.assembleMemoryContext(request.getQuery());
            List<Message> messages = new ArrayList<>();

            if (!memoryContext.isBlank()) {
                messages.add(new org.springframework.ai.chat.messages.SystemMessage(
                        "历史记忆:\n" + memoryContext));
            }

            // 添加当前用户查询
            messages.add(new UserMessage(request.getQuery()));

            // 构建 Prompt
            Prompt prompt = buildThinkingPrompt(messages, request.getEnableThinking());

            // 调用LLM
            org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(prompt);
            ThinkingExtractor.ThinkingResult thinkingResult = ThinkingExtractor.extractFromSpringAi(aiResponse);
            String answer = thinkingResult.textContent();

            // 通过 ContextStore 追加历史
            AgentMessage userInput = AgentMessage.builder()
                    .senderId(request.getUserId())
                    .content(request.getQuery())
                    .timestamp(System.currentTimeMillis())
                    .metadata(Map.of("role", "user"))
                    .build();
            ctx.appendHistory(userInput);

            AgentMessage assistantOutput = AgentMessage.builder()
                    .senderId("assistant")
                    .content(answer)
                    .timestamp(System.currentTimeMillis())
                    .metadata(Map.of("role", "assistant"))
                    .build();
            ctx.appendHistory(assistantOutput);

            return ChatResponse.builder()
                    .answer(answer)
                    .thinkingContent(thinkingResult.hasThinking() ? thinkingResult.thinkingContent() : null)
                    .sessionId(sessionId)
                    .ragDegraded(false)
                    .build();
        } catch (ChatException e) {
            throw e;
        } catch (Exception e) {
            log.error("MultiTurnChat LLM call failed: {}", e.getMessage(), e);
            throw new ChatException(Constants.ErrorCode.CHAT_LLM_ERROR, "服务繁忙请稍后重试", e);
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(ChatRequest request) {
        String sessionId = ensureSessionId(request);
        String finalSessionId = sessionId;

        return Flux.defer(() -> {
            ContextStore ctx = contextStoreFactory.getOrCreate(
                    finalSessionId, request.getUserId(), EngineType.GRAPH, false, null);

            String memoryContext = ctx.assembleMemoryContext(request.getQuery());
            List<Message> messages = new ArrayList<>();

            if (!memoryContext.isBlank()) {
                messages.add(new org.springframework.ai.chat.messages.SystemMessage(
                        "历史记忆:\n" + memoryContext));
            }

            messages.add(new UserMessage(request.getQuery()));

            // 保存用户消息
            AgentMessage userInput = AgentMessage.builder()
                    .senderId(request.getUserId())
                    .content(request.getQuery())
                    .timestamp(System.currentTimeMillis())
                    .metadata(Map.of("role", "user"))
                    .build();
            ctx.appendHistory(userInput);

            StringBuilder answerBuilder = new StringBuilder();

            return chatModel.stream(buildThinkingPrompt(messages, request.getEnableThinking()))
                    .flatMap(aiResponse -> {
                        ThinkingExtractor.ThinkingResult result = ThinkingExtractor.extractFromSpringAi(aiResponse);
                        String content = result.textContent();
                        answerBuilder.append(content);

                        Flux<StreamEvent> events = Flux.empty();
                        if (result.hasThinking()) {
                            events = events.concatWith(Flux.just(StreamEvent.thinking(result.thinkingContent(), finalSessionId)));
                        }
                        if (!content.isEmpty()) {
                            events = events.concatWith(Flux.just(StreamEvent.textDelta(content, finalSessionId)));
                        }
                        return events;
                    })
                    .concatWith(Flux.defer(() -> {
                        String fullAnswer = answerBuilder.toString();
                        AgentMessage assistantOutput = AgentMessage.builder()
                                .senderId("assistant")
                                .content(fullAnswer)
                                .timestamp(System.currentTimeMillis())
                                .metadata(Map.of("role", "assistant"))
                                .build();
                        ctx.appendHistory(assistantOutput);
                        return Flux.just(StreamEvent.done(false, null, finalSessionId));
                    }));
        });
    }

    private String ensureSessionId(ChatRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
            request.setSessionId(sessionId);
        }
        return sessionId;
    }

    /**
     * 构建 Prompt — enableThinking=true 时注入 DashScopeChatOptions
     */
    private Prompt buildThinkingPrompt(List<Message> messages, Boolean enableThinking) {
        if (Boolean.TRUE.equals(enableThinking)) {
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .enableThinking(true)
                    .build();
            return new Prompt(messages, options);
        }
        return new Prompt(messages);
    }

}
