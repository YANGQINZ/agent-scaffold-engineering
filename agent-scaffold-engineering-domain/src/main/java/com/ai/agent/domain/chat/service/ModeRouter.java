package com.ai.agent.domain.chat.service;

import com.ai.agent.domain.agent.service.TaskRuntime;
import com.ai.agent.domain.chat.service.strategy.ChatStrategy;
import com.ai.agent.domain.chat.service.strategy.MultiTurnChatStrategy;
import com.ai.agent.domain.chat.service.strategy.SimpleChatStrategy;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.enums.ChatMode;
import com.ai.agent.types.exception.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话模式路由器 — 根据ChatMode选择对应的策略
 */
@Slf4j
@Service
public class ModeRouter {

    private final Map<ChatMode, ChatStrategy> strategyMap;

    public ModeRouter(List<ChatStrategy> strategies) {
        this.strategyMap = new HashMap<>();
        for (ChatStrategy strategy : strategies) {
            if (strategy instanceof SimpleChatStrategy) {
                strategyMap.put(ChatMode.SIMPLE, strategy);
            } else if (strategy instanceof MultiTurnChatStrategy) {
                strategyMap.put(ChatMode.MULTI_TURN, strategy);
            } else if (strategy instanceof TaskRuntime) {
                strategyMap.put(ChatMode.AGENT, strategy);
            }
        }
    }

    /**
     * 根据对话模式路由到对应策略
     */
    public ChatStrategy route(ChatMode mode) {
        ChatStrategy strategy = strategyMap.get(mode);
        if (strategy == null) {
            log.error("Unsupported chat mode: {}", mode);
            throw new ChatException(Constants.ErrorCode.CHAT_MODE_UNSUPPORTED,
                    "不支持的对话模式: " + mode.getDescription());
        }
        return strategy;
    }

}
