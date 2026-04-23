package com.ai.agent.domain.chat.service.strategy;

import com.ai.agent.domain.chat.model.entity.ChatMessage;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.domain.chat.service.ChatMemoryManager;
import com.ai.agent.types.common.Constants;
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
        // 如果sessionId为空则生成新的
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        request.setSessionId(sessionId);

        try {
            // 加载历史消息
            List<ChatMessage> history = memoryManager.load(sessionId);

            // 转换为Spring AI Message列表
            List<org.springframework.ai.chat.messages.Message> messages = convertToSpringAiMessages(history);

            // 添加当前用户查询
            messages.add(new UserMessage(request.getQuery()));

            // 调用LLM
            org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(new Prompt(messages));
            String answer = aiResponse.getResult().getOutput().getText();

            // 保存用户消息和助手消息
            ChatMessage userMsg = ChatMessage.builder()
                    .sessionId(sessionId)
                    .role(MessageRole.USER)
                    .content(request.getQuery())
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            memoryManager.save(userMsg);

            ChatMessage assistantMsg = ChatMessage.builder()
                    .sessionId(sessionId)
                    .role(MessageRole.ASSISTANT)
                    .content(answer)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            memoryManager.save(assistantMsg);

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
        // 如果sessionId为空则生成新的
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        request.setSessionId(sessionId);
        String finalSessionId = sessionId;

        return Flux.defer(() -> {
            // 加载历史消息
            List<ChatMessage> history = memoryManager.load(finalSessionId);

            // 转换为Spring AI Message列表
            List<org.springframework.ai.chat.messages.Message> messages = convertToSpringAiMessages(history);

            // 添加当前用户查询
            messages.add(new UserMessage(request.getQuery()));

            // 保存用户消息
            ChatMessage userMsg = ChatMessage.builder()
                    .sessionId(finalSessionId)
                    .role(MessageRole.USER)
                    .content(request.getQuery())
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            memoryManager.save(userMsg);

            StringBuilder answerBuilder = new StringBuilder();

            return chatModel.stream(new Prompt(messages))
                    .map(aiResponse -> {
                        String content = aiResponse.getResult() != null && aiResponse.getResult().getOutput() != null
                                ? aiResponse.getResult().getOutput().getText() : "";
                        answerBuilder.append(content);
                        return StreamEvent.textDelta(content, finalSessionId);
                    })
                    .concatWith(Flux.defer(() -> {
                        // 流结束后保存助手消息
                        String fullAnswer = answerBuilder.toString();
                        ChatMessage assistantMsg = ChatMessage.builder()
                                .sessionId(finalSessionId)
                                .role(MessageRole.ASSISTANT)
                                .content(fullAnswer)
                                .createdAt(java.time.LocalDateTime.now())
                                .build();
                        memoryManager.save(assistantMsg);
                        return Flux.just(StreamEvent.done(false, null, finalSessionId));
                    }));
        });
    }

    /**
     * 将领域ChatMessage转换为Spring AI Message
     */
    private List<org.springframework.ai.chat.messages.Message> convertToSpringAiMessages(List<ChatMessage> history) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            if (msg.getRole() == MessageRole.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
            // SYSTEM messages are skipped in history for now
        }
        return messages;
    }

}
