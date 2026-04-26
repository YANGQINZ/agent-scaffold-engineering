package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.domain.memory.service.MemoryFacade;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.exception.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class MultiTurnChatStrategy implements ChatStrategy {

    private final ChatModel chatModel;
    private final MemoryFacade memoryFacade;

    public MultiTurnChatStrategy(ChatModel chatModel, MemoryFacade memoryFacade) {
        this.chatModel = chatModel;
        this.memoryFacade = memoryFacade;
    }

    @Override
    public ChatResponse execute(ChatRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        request.setSessionId(sessionId);

        try {
            // 加载上下文（热层 + 冷层语义检索）
            List<org.springframework.ai.chat.messages.Message> messages =
                    memoryFacade.assembleContext(sessionId, request.getQuery());

            // 添加当前用户查询
            messages.add(new UserMessage(request.getQuery()));

            // 调用LLM
            org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(new Prompt(messages));
            String answer = aiResponse.getResult().getOutput().getText();

            // 保存用户消息和助手消息
            int queryTokens = request.getQuery().length() / 4;
            int answerTokens = answer.length() / 4;
            memoryFacade.appendMessage(sessionId, "user", request.getQuery(), queryTokens);
            memoryFacade.appendMessage(sessionId, "assistant", answer, answerTokens);

            return ChatResponse.builder()
                    .answer(answer)
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
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        request.setSessionId(sessionId);
        String finalSessionId = sessionId;

        return Flux.defer(() -> {
            // 加载上下文
            List<org.springframework.ai.chat.messages.Message> messages =
                    memoryFacade.assembleContext(finalSessionId, request.getQuery());

            // 添加当前用户查询
            messages.add(new UserMessage(request.getQuery()));

            // 保存用户消息
            int queryTokens = request.getQuery().length() / 4;
            memoryFacade.appendMessage(finalSessionId, "user", request.getQuery(), queryTokens);

            StringBuilder answerBuilder = new StringBuilder();

            return chatModel.stream(new Prompt(messages))
                    .map(aiResponse -> {
                        String content = aiResponse.getResult() != null && aiResponse.getResult().getOutput() != null
                                ? aiResponse.getResult().getOutput().getText() : "";
                        answerBuilder.append(content);
                        return StreamEvent.textDelta(content, finalSessionId);
                    })
                    .concatWith(Flux.defer(() -> {
                        String fullAnswer = answerBuilder.toString();
                        int answerTokens = fullAnswer.length() / 4;
                        memoryFacade.appendMessage(finalSessionId, "assistant", fullAnswer, answerTokens);
                        return Flux.just(StreamEvent.done(false, null, finalSessionId));
                    }));
        });
    }

}
