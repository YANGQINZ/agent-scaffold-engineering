package com.ai.agent.infrastructure;

import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;
import com.ai.agent.infrastructure.persistent.dao.IKnowledgeBaseDao;
import com.ai.agent.infrastructure.persistent.po.KnowledgeBasePO;
import com.ai.agent.infrastructure.persistent.repository.KnowledgeBaseRepositoryImpl;
import com.ai.agent.types.enums.OwnerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * KnowledgeBaseRepositoryImpl 单元测试
 * 验证新增的fileName/fileType字段映射
 */
class KnowledgeBaseRepositoryImplTest {

    private IKnowledgeBaseDao dao;
    private KnowledgeBaseRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        dao = mock(IKnowledgeBaseDao.class);
        repository = new KnowledgeBaseRepositoryImpl(dao);
    }

    @Test
    void save_mapsDomainToPO_includesFileNameAndFileType() {
        KnowledgeBase kb = KnowledgeBase.builder()
                .baseId(1L)
                .name("测试知识库")
                .description("描述")
                .fileName("test.pdf")
                .fileType("pdf")
                .ownerType(OwnerType.USER)
                .ownerId("user-1")
                .docCount(5)
                .createdAt(LocalDateTime.now())
                .build();

        repository.save(kb);

        verify(dao).insert(argThat(po -> {
            assertEquals("base-1", po.getBaseId());
            assertEquals("测试知识库", po.getName());
            assertEquals("描述", po.getDescription());
            assertEquals("test.pdf", po.getFileName());
            assertEquals("pdf", po.getFileType());
            assertEquals("USER", po.getOwnerType());
            assertEquals("user-1", po.getOwnerId());
            assertEquals(5, po.getDocCount());
            return true;
        }));
    }

    @Test
    void save_nullFileNameAndFileType_mapsCorrectly() {
        KnowledgeBase kb = KnowledgeBase.builder()
                .baseId(1L)
                .name("无文件知识库")
                .ownerType(OwnerType.ADMIN)
                .ownerId("admin-1")
                .docCount(0)
                .build();

        repository.save(kb);

        verify(dao).insert(argThat(po -> {
            assertNull(po.getFileName());
            assertNull(po.getFileType());
            return true;
        }));
    }

    @Test
    void findById_mapsPOtoDomain_includesFileNameAndFileType() {
        KnowledgeBasePO po = new KnowledgeBasePO();
        po.setBaseId(1L);
        po.setName("查询知识库");
        po.setDescription("查询描述");
        po.setFileName("doc.docx");
        po.setFileType("docx");
        po.setOwnerType("USER");
        po.setOwnerId("user-2");
        po.setDocCount(10);
        po.setCreatedAt(LocalDateTime.now());

        when(dao.selectById("base-3")).thenReturn(po);

        KnowledgeBase result = repository.findById("base-3");

        assertNotNull(result);
        assertEquals("base-3", result.getBaseId());
        assertEquals("查询知识库", result.getName());
        assertEquals("doc.docx", result.getFileName());
        assertEquals("docx", result.getFileType());
        assertEquals(OwnerType.USER, result.getOwnerType());
        assertEquals(10, result.getDocCount());
    }

    @Test
    void findById_notFound_returnsNull() {
        when(dao.selectById("not-exist")).thenReturn(null);

        KnowledgeBase result = repository.findById("not-exist");
        assertNull(result);
    }
}
