package com.ai.agent.trigger.http;

import com.ai.agent.domain.knowledge.model.entity.KnowledgeBaseResponse;
import com.ai.agent.domain.knowledge.service.IKnowledgeService;
import com.ai.agent.types.model.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeControllerTest {

    private KnowledgeController controller;
    private IKnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        knowledgeService = mock(IKnowledgeService.class);
        controller = new KnowledgeController(knowledgeService);
    }

    @Test
    void createKnowledgeBase_invalidOwnerType_returnsErrorResponse() {
        Response<?> response = controller.createKnowledgeBase("测试", "", "INVALID_TYPE", "");
        assertNotNull(response);
        assertNotEquals("0000", response.getErrorCode());
        // 不应调用 knowledgeService
        verifyNoInteractions(knowledgeService);
    }

    @Test
    void createKnowledgeBase_nullName_returnsErrorResponse() {
        Response<?> response = controller.createKnowledgeBase(null, "", "USER", "");
        assertNotNull(response);
        assertNotEquals("0000", response.getErrorCode());
        verifyNoInteractions(knowledgeService);
    }

    @Test
    void createKnowledgeBase_blankName_returnsErrorResponse() {
        Response<?> response = controller.createKnowledgeBase("  ", "", "USER", "");
        assertNotNull(response);
        assertNotEquals("0000", response.getErrorCode());
        verifyNoInteractions(knowledgeService);
    }

    @Test
    void createKnowledgeBase_validParams_delegatesToService() {
        when(knowledgeService.createKnowledgeBase("测试", "描述", "USER", "u1"))
                .thenReturn(KnowledgeBaseResponse.builder()
                        .baseId("id-123").name("测试").description("描述").docCount(0).build());

        Response<?> response = controller.createKnowledgeBase("测试", "描述", "USER", "u1");
        assertEquals("0000", response.getErrorCode());
        verify(knowledgeService).createKnowledgeBase("测试", "描述", "USER", "u1");
    }
}
