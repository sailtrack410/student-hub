package com.studenthub.modules.aichat;

import com.studenthub.modules.aichat.service.impl.AiEmbeddingModelLockService;
import com.studenthub.modules.rag.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 知识库已有向量时嵌入模型只读，向量清空/ RAG 停用后才解锁。 */
@ExtendWith(MockitoExtension.class)
class AiEmbeddingModelLockServiceTest {

    @Mock
    private JdbcTemplate ragJdbcTemplate;
    @Mock
    private RagProperties ragProperties;

    private AiEmbeddingModelLockService service;

    @BeforeEach
    void setUp() {
        service = new AiEmbeddingModelLockService(ragJdbcTemplate, ragProperties);
    }

    @Test
    void locksWhenVectorsExistAndReportsModel() {
        when(ragProperties.isEnabled()).thenReturn(true);
        when(ragJdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(12L);
        when(ragJdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("embedding_model", "text-embedding-v3", "c", 12L)));

        AiEmbeddingModelLockService.LockState lock = service.current();

        assertTrue(lock.locked());
        assertEquals("text-embedding-v3", lock.model());
        assertEquals(12L, lock.vectorChunks());
        assertTrue(lock.message().contains("不可更改"));
    }

    @Test
    void unlocksWhenNoVectors() {
        when(ragProperties.isEnabled()).thenReturn(true);
        when(ragJdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        AiEmbeddingModelLockService.LockState lock = service.current();

        assertFalse(lock.locked());
        verify(ragJdbcTemplate, never()).queryForList(anyString());
    }

    @Test
    void doesNotQueryRagDatabaseWhenDisabled() {
        when(ragProperties.isEnabled()).thenReturn(false);

        AiEmbeddingModelLockService.LockState lock = service.current();

        assertFalse(lock.locked());
        verify(ragJdbcTemplate, never()).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    void databaseFailureDoesNotBreakAdminPage() {
        when(ragProperties.isEnabled()).thenReturn(true);
        when(ragJdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new IllegalStateException("connection refused"));

        AiEmbeddingModelLockService.LockState lock = service.current();

        assertFalse(lock.locked());
        assertTrue(lock.message().contains("暂时无法读取"));
    }
}
