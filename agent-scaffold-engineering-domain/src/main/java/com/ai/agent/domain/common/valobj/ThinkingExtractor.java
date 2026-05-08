package com.ai.agent.domain.common.valobj;

import org.springframework.ai.chat.model.ChatResponse;

/**
 * 思考内容提取器 — 从 Spring AI ChatResponse 中提取思考过程
 *
 * 纯工具类，无 Spring 依赖。
 * Spring AI：reasoningContent 位于 AssistantMessage.metadata["reasoningContent"]
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
     * 提取结果记录
     */
    public record ThinkingResult(String thinkingContent, String textContent) {
        public boolean hasThinking() {
            return thinkingContent != null && !thinkingContent.isBlank();
        }
    }
}
