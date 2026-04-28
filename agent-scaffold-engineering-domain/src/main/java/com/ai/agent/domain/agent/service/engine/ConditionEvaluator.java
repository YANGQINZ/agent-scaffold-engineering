package com.ai.agent.domain.agent.service.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 条件评估器 — 策略链模式
 *
 * 评估优先级：关键字精确匹配 → 数值/正则比较 → LLM语义判断
 * YAML 条件写法约定：
 *   "type==faq"          → 关键字精确匹配（==运算符）
 *   "score>0.8"          → 数值比较（>/>=/</<=运算符）
 *   "llm:intent:research" → LLM语义判断（llm:前缀触发）
 *   "简单包含"            → 简单包含匹配（降级方案）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConditionEvaluator {

    /** LLM 条件前缀 */
    private static final String LLM_PREFIX = "llm:";

    /**
     * 评估条件是否满足
     *
     * @param nodeOutput 节点输出内容
     * @param condition  条件表达式
     * @param chatModel  ChatModel（LLM降级判断时使用）
     * @return true=条件满足
     */
    public boolean evaluate(String nodeOutput, String condition, ChatModel chatModel) {
        if (nodeOutput == null || condition == null) {
            return false;
        }

        // 1. LLM 语义判断（llm: 前缀）
        if (condition.startsWith(LLM_PREFIX)) {
            String description = condition.substring(LLM_PREFIX.length());
            return evaluateWithLlm(nodeOutput, description, chatModel);
        }

        // 2. 关键字精确匹配（== 运算符）
        if (condition.contains("==")) {
            return evaluateEquality(nodeOutput, condition);
        }

        // 3. 数值比较（>/>=/</<= 运算符）
        if (condition.matches(".*[><]=?.*")) {
            return evaluateNumeric(nodeOutput, condition);
        }

        // 4. 简单包含匹配（降级方案）
        boolean contains = nodeOutput.contains(condition);
        if (contains) {
            log.debug("条件匹配(包含): condition={}, 匹配成功", condition);
        }
        return contains;
    }

    /**
     * 关键字精确匹配 — condition 格式: "key==value"
     */
    private boolean evaluateEquality(String nodeOutput, String condition) {
        String[] parts = condition.split("==", 2);
        if (parts.length != 2) {
            return false;
        }
        String expected = parts[1].trim();
        boolean matched = nodeOutput.contains(parts[0].trim() + "==" + expected)
                || nodeOutput.toLowerCase().contains(expected.toLowerCase());
        log.debug("条件匹配(精确): condition={}, matched={}", condition, matched);
        return matched;
    }

    /**
     * 数值比较 — condition 格式: "key>0.8" / "key>=0.8" / "key<0.8" / "key<=0.8"
     */
    private boolean evaluateNumeric(String nodeOutput, String condition) {
        try {
            String operator = condition.matches(".*[><]=.*")
                    ? condition.replaceAll(".*([><]=).*", "$1")
                    : condition.replaceAll(".*([><]).*", "$1");

            String[] parts = condition.split("[><]=?", 2);
            if (parts.length != 2) {
                return false;
            }

            double threshold = Double.parseDouble(parts[1].trim());
            String numberStr = nodeOutput.replaceAll(".*?(\\d+\\.?\\d*).*", "$1");
            if (numberStr.equals(nodeOutput)) {
                return false; // 输出中无数值
            }
            double actual = Double.parseDouble(numberStr);

            boolean result = switch (operator) {
                case ">" -> actual > threshold;
                case ">=" -> actual >= threshold;
                case "<" -> actual < threshold;
                case "<=" -> actual <= threshold;
                default -> false;
            };
            log.debug("条件匹配(数值): condition={}, actual={}, threshold={}, result={}",
                    condition, actual, threshold, result);
            return result;
        } catch (Exception e) {
            log.warn("数值条件评估异常: condition={}, error={}", condition, e.getMessage());
            return false;
        }
    }

    /**
     * LLM 语义判断 — 使用 ChatModel 判断节点输出是否满足语义条件
     */
    private boolean evaluateWithLlm(String nodeOutput, String description, ChatModel chatModel) {
        try {
            String evalPrompt = "你是一个条件评估器。请根据以下节点输出判断是否满足指定条件。\n\n" +
                    "条件: " + description + "\n\n" +
                    "节点输出: " + nodeOutput + "\n\n" +
                    "请只回答'是'或'否'。";

            Prompt prompt = new Prompt(List.of(
                    new SystemMessage("你是一个精确的条件判断器，只回答'是'或'否'。"),
                    new UserMessage(evalPrompt)
            ));
            String result = Objects.requireNonNull(chatModel.call(prompt).getResult().getOutput().getText()).trim();
            boolean matched = result.startsWith("是") || result.toLowerCase().startsWith("yes");
            log.debug("条件匹配(LLM): description={}, matched={}", description, matched);
            return matched;
        } catch (Exception e) {
            log.warn("LLM条件评估异常: description={}, error={}", description, e.getMessage());
            return false;
        }
    }

}
