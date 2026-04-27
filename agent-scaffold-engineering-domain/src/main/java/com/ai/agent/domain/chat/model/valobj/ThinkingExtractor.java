package com.ai.agent.domain.chat.model.valobj;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ThinkingBlock;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.stream.Collectors;

/**
 * 思考内容提取器 — 统一从 Spring AI ChatResponse 和 agentscope Msg 中提取思考过程
 *
 * 纯工具类，无 Spring 依赖。
 * Spring AI：reasoningContent 位于 AssistantMessage.metadata["reasoningContent"]
 * AgentScope：思考内容位于 Msg.getContentBlocks(ThinkingBlock.class)
 */
public final class ThinkingExtractor {

    private static final String REASONING_CONTENT_KEY = "reasoningContent";

    private ThinkingExtractor() {}

    /**
     * 从 Spring AI ChatResponse 中提取思考内容和正文
     */
    public static ThinkingResult extractFromSpringAi(ChatResponse aiResponse) {
        if (aiResponse == null || aiResponse.getResult() == null || aiResponse.getResult().getOutput() == null) {
            return new ThinkingResult(null, "");
        }

        String textContent = aiResponse.getResult().getOutput().getText();
        if (textContent == null) {
            textContent = "";
        }

        // 从 AssistantMessage.metadata 中提取 reasoningContent
        String thinkingContent = null;
        var metadata = aiResponse.getResult().getOutput().getMetadata();
        if (metadata != null && metadata.containsKey(REASONING_CONTENT_KEY)) {
            Object reasoningObj = metadata.get(REASONING_CONTENT_KEY);
            if (reasoningObj instanceof String reasoning && !reasoning.isBlank()) {
                thinkingContent = reasoning;
            }
        }

        return new ThinkingResult(thinkingContent, textContent);
    }

    /**
     * 从 agentscope Msg 中提取思考内容和正文
     */
    public static ThinkingResult extractFromAgentScope(Msg msg) {
        if (msg == null) {
            return new ThinkingResult(null, "");
        }

        // 从 ThinkingBlock 列表提取思考内容
        String thinkingContent = null;
        var thinkingBlocks = msg.getContentBlocks(ThinkingBlock.class);
        if (thinkingBlocks != null && !thinkingBlocks.isEmpty()) {
            thinkingContent = thinkingBlocks.stream()
                    .map(ThinkingBlock::getThinking)
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.joining());
        }

        // 正文从 getTextContent() 获取（仅提取 TextBlock）
        String textContent = msg.getTextContent();
        if (textContent == null) {
            textContent = "";
        }

        return new ThinkingResult(thinkingContent, textContent);
    }

    /**
     * 提取结果记录
     */
    public record ThinkingResult(String thinkingContent, String textContent) {
        public boolean hasThinking() {
            return thinkingContent != null && !thinkingContent.isBlank();
        }
    }
}
