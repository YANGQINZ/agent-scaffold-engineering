package com.ai.agent.infrastructure;

import com.ai.agent.api.model.knowledge.KnowledgeBaseResponseDTO;
import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;
import com.ai.agent.domain.knowledge.model.entity.Document;
import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.model.entity.KnowledgeBaseResponse;
import com.ai.agent.domain.knowledge.repository.IDocumentChunkRepository;
import com.ai.agent.domain.knowledge.repository.IDocumentRepository;
import com.ai.agent.domain.knowledge.repository.IKnowledgeBaseRepository;
import com.ai.agent.domain.knowledge.DocumentProcessor;
import com.ai.agent.domain.knowledge.service.business.KnowledgeServiceImpl;
import com.ai.agent.types.enums.DocumentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeServiceImplTest {

    private IKnowledgeBaseRepository kbRepo;
    private IDocumentRepository docRepo;
    private IDocumentChunkRepository chunkRepo;
    private DocumentProcessor processor;
    private KnowledgeServiceImpl service;

    @BeforeEach
    void setUp() {
        kbRepo = mock(IKnowledgeBaseRepository.class);
        docRepo = mock(IDocumentRepository.class);
        chunkRepo = mock(IDocumentChunkRepository.class);
        processor = mock(DocumentProcessor.class);
        service = new KnowledgeServiceImpl(kbRepo, docRepo, chunkRepo, processor);
    }

    @Test
    void createKnowledgeBase_validInput_returnsDTO() {
        KnowledgeBaseResponse result = service.createKnowledgeBase("测试库", "描述", "USER", "user1");
        assertNotNull(result);
        assertEquals("测试库", result.getName());
        assertEquals("描述", result.getDescription());
        assertEquals(0, result.getDocCount());
        verify(kbRepo).save(any(KnowledgeBase.class));
    }

    @Test
    void createKnowledgeBase_invalidOwnerType_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createKnowledgeBase("测试", "", "INVALID", ""));
    }

    @Test
    void uploadDocument_validContent_returnsDocId() throws Exception {
        when(processor.process(anyString(), anyString(), anyString()))
                .thenReturn(List.of(
                        DocumentChunk.builder().chunkId(1L).content("chunk1").build()
                ));

        // 模拟文件内容
        byte[] content = "测试文档内容".getBytes();
        org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.txt");
        when(file.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(content));

        String docId = service.uploadDocument("base-123", file, "user1");

        assertNotNull(docId);
        verify(docRepo).save(any(Document.class));
        verify(chunkRepo).saveBatch(anyList());
        verify(docRepo).updateStatus(anyString(), eq(DocumentStatus.COMPLETED));
    }

    @Test
    void uploadDocument_processingFails_statusSetToFailed() throws Exception {
        when(processor.process(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("处理失败"));

        byte[] content = "内容".getBytes();
        org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.txt");
        when(file.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(content));

        assertThrows(RuntimeException.class,
                () -> service.uploadDocument("base-123", file, "user1"));
        verify(docRepo).updateStatus(anyString(), eq(DocumentStatus.FAILED));
    }
}
