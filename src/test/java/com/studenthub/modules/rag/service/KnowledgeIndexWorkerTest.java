package com.studenthub.modules.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.rag.config.RagProperties;
import com.studenthub.modules.rag.entity.KnowledgeBaseEntity;
import com.studenthub.modules.rag.entity.KnowledgeDocumentEntity;
import com.studenthub.modules.rag.mapper.KnowledgeDocumentMapper;
import com.studenthub.modules.rag.mapper.KnowledgeBaseMapper;
import com.studenthub.service.AiChatService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeIndexWorkerTest {
    private KnowledgeDocumentMapper documents;
    private KnowledgeBaseService bases;
    private FileStorageService files;
    private KnowledgeDocumentParser parser;
    private EmbeddingService embeddings;
    private JdbcTemplate jdbc;
    private KnowledgeIndexWorker worker;
    private KnowledgeDocumentEntity doc;

    @BeforeEach void setUp() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:rag-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        String columns = "knowledge_base_id BIGINT,document_id BIGINT,ordinal INT,title_path VARCHAR,content VARCHAR,content_hash VARCHAR,metadata VARCHAR,embedding VARCHAR,embedding_model VARCHAR,embedding_dim INT";
        jdbc.execute("CREATE TABLE ai_knowledge_chunk(" + columns + ",UNIQUE(document_id,ordinal))");
        jdbc.execute("CREATE TABLE ai_knowledge_chunk_stage(" + columns + ",index_token VARCHAR)");
        jdbc.update("INSERT INTO ai_knowledge_chunk(knowledge_base_id,document_id,ordinal,content) VALUES(1,10,0,'old answer'),(1,20,0,'other document')");
        documents = mock(KnowledgeDocumentMapper.class);
        bases = mock(KnowledgeBaseService.class);
        files = mock(FileStorageService.class);
        parser = mock(KnowledgeDocumentParser.class);
        embeddings = mock(EmbeddingService.class);
        var metadataDs = new JdbcDataSource();
        metadataDs.setURL("jdbc:h2:mem:metadata-" + UUID.randomUUID());
        worker = new KnowledgeIndexWorker(documents, bases, files, parser, embeddings, new RagProperties(),
                new ObjectMapper(), new DataSourceTransactionManager(metadataDs), jdbc);
        doc = new KnowledgeDocumentEntity(); doc.setId(10L); doc.setKnowledgeBaseId(1L);
        doc.setFileId(100L); doc.setFileName("notice.txt"); doc.setIndexAttempts(1);
        when(documents.lockOwned(10L, "owner")).thenReturn(doc);
        when(documents.finish(eq(10L), eq("owner"), anyInt(), any(LocalDateTime.class))).thenReturn(1);
    }

    @Test void replacementCommitsCompleteGenerationAndPreservesOtherDocuments() {
        jdbc.update("INSERT INTO ai_knowledge_chunk_stage(knowledge_base_id,document_id,ordinal,content,index_token) VALUES(1,10,0,'new answer','owner'),(1,10,1,'new detail','owner')");
        worker.replace(doc, "owner", 2);
        assertEquals(List.of("new answer", "new detail"), jdbc.queryForList("SELECT content FROM ai_knowledge_chunk WHERE document_id=10 ORDER BY ordinal", String.class));
        assertEquals("other document", jdbc.queryForObject("SELECT content FROM ai_knowledge_chunk WHERE document_id=20", String.class));
        verify(documents).finish(eq(10L), eq("owner"), eq(2), any(LocalDateTime.class));
    }

    @Test void insertFailureRollsBackDeletionAndLeavesExistingIndexReadable() {
        jdbc.update("INSERT INTO ai_knowledge_chunk_stage(knowledge_base_id,document_id,ordinal,content,index_token) VALUES(1,10,0,'bad one','owner'),(1,10,0,'bad two','owner')");
        assertThrows(Exception.class, () -> worker.replace(doc, "owner", 2));
        assertEquals("old answer", jdbc.queryForObject("SELECT content FROM ai_knowledge_chunk WHERE document_id=10", String.class));
        verify(documents, never()).finish(anyLong(), anyString(), anyInt(), any());
    }

    @Test void staleOwnerCannotReplaceIndex() {
        when(documents.lockOwned(10L, "owner")).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> worker.replace(doc, "owner", 1));
        assertEquals("old answer", jdbc.queryForObject("SELECT content FROM ai_knowledge_chunk WHERE document_id=10", String.class));
    }

    @Test void providerFailureQueuesRetryAndKeepsPriorIndex() {
        var base = new KnowledgeBaseEntity(); base.setId(1L); base.setChunkSize(800); base.setChunkOverlap(120);
        when(bases.requireBase(1L)).thenReturn(base);
        when(files.readContent(100L)).thenReturn(new FileStorageService.StoredContent("new answer".getBytes(), "text/plain", "notice.txt"));
        when(parser.parse(any(), anyString())).thenReturn(List.of(new KnowledgeDocumentParser.Section(null, 1, "new answer")));
        when(documents.progress(eq(10L), eq("owner"), anyInt(), anyInt(), any())).thenReturn(1);
        when(embeddings.embedBatch(anyList())).thenThrow(new IllegalStateException("provider timeout"));
        worker.process(doc, "owner", 3);
        verify(documents).fail(eq(10L), eq("owner"), eq("QUEUED"), eq("provider timeout"), any(LocalDateTime.class));
        assertEquals("old answer", jdbc.queryForObject("SELECT content FROM ai_knowledge_chunk WHERE document_id=10", String.class));
        verify(documents, never()).lockOwned(anyLong(), anyString());
    }

    @Test void uploadCommitsQueuedMetadataWithoutParsingEmbeddingOrVectorAccess() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "upload-test"),
                KnowledgeDocumentEntity.class);
        var baseMapper = mock(KnowledgeBaseMapper.class);
        var vector = mock(JdbcTemplate.class);
        var retrieval = mock(RetrievalService.class);
        var chat = mock(AiChatService.class);
        var properties = new RagProperties(); properties.setEnabled(true);
        var service = new KnowledgeBaseService(baseMapper, documents, files, parser, embeddings,
                retrieval, chat, properties, new ObjectMapper(), vector);
        var base = new KnowledgeBaseEntity(); base.setId(1L);
        when(baseMapper.selectById(1L)).thenReturn(base);
        when(files.storeKnowledgeDocument(any(), eq("notice.txt"), eq("text/plain"), eq(7L), anyInt()))
                .thenReturn(new FileStorageService.StoredFile(100L, "/files/100", "notice.txt"));
        var existing = new KnowledgeDocumentEntity(); existing.setChunkCount(42);
        when(documents.selectList(any())).thenReturn(List.of(existing, new KnowledgeDocumentEntity()));

        var queued = service.upload(7L, 1L, "new notice".getBytes(), "notice.txt", "text/plain");

        assertEquals("QUEUED", queued.getStatus());
        assertEquals(0, queued.getIndexAttempts());
        assertEquals(0, queued.getProcessedChunks());
        assertEquals(0, queued.getChunkCount());
        assertNotNull(queued.getNextAttemptAt());
        verify(documents).insert(queued);
        var counts = org.mockito.ArgumentCaptor.forClass(KnowledgeBaseEntity.class);
        verify(baseMapper).updateById(counts.capture());
        assertEquals(2, counts.getValue().getDocumentCount());
        assertEquals(42, counts.getValue().getChunkCount());
        verifyNoInteractions(parser, embeddings, vector, retrieval, chat);
    }
}
