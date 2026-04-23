package com.ai.agent.test;

import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.service.ChatFacade;
import com.ai.agent.types.enums.ChatMode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ChatIntegrationTest {

    @Resource
    private ChatFacade chatFacade;

    @Test
    public void testSimpleChat() {
        ChatRequest request = ChatRequest.builder()
                .userId("test-user")
                .query("你好，请介绍一下你自己")
                .mode(ChatMode.SIMPLE)
                .ragEnabled(false)
                .build();

        ChatResponse response = chatFacade.chat(request);
        assertNotNull(response);
        assertNotNull(response.getAnswer());
        assertFalse(response.getAnswer().isEmpty());
        log.info("SimpleChat响应: {}", response.getAnswer());
    }

    @Test
    public void testMultiTurnChat() {
        // First turn
        ChatRequest request1 = ChatRequest.builder()
                .userId("test-user")
                .sessionId("test-session-001")
                .query("我叫小明")
                .mode(ChatMode.MULTI_TURN)
                .ragEnabled(false)
                .build();

        ChatResponse response1 = chatFacade.chat(request1);
        assertNotNull(response1);
        assertNotNull(response1.getAnswer());
        assertNotNull(response1.getSessionId());

        // Second turn - should remember context
        ChatRequest request2 = ChatRequest.builder()
                .userId("test-user")
                .sessionId(response1.getSessionId())
                .query("我叫什么名字？")
                .mode(ChatMode.MULTI_TURN)
                .ragEnabled(false)
                .build();

        ChatResponse response2 = chatFacade.chat(request2);
        assertNotNull(response2);
        assertNotNull(response2.getAnswer());
        // The LLM should remember "小明" from previous turn
        log.info("MultiTurn第二回合响应: {}", response2.getAnswer());
    }
}
