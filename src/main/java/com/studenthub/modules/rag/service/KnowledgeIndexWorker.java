package com.studenthub.modules.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.util.Digests;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.rag.config.RagProperties;
import com.studenthub.modules.rag.entity.KnowledgeBaseEntity;
import com.studenthub.modules.rag.entity.KnowledgeDocumentEntity;
import com.studenthub.modules.rag.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Persistent, leased indexing jobs. Embedding work never holds a request or metadata transaction. */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndexWorker {
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final FileStorageService fileStorageService;
    private final KnowledgeDocumentParser parser;
    private final EmbeddingService embeddingService;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    @Qualifier("ragJdbcTemplate") private final JdbcTemplate vectorJdbc;

    @Scheduled(fixedDelayString = "${studenthub.rag.index-poll-ms:15000}", initialDelayString = "${studenthub.rag.index-initial-delay-ms:15000}", scheduler = "knowledgeIndexScheduler")
    public void dispatch() {
        if (!properties.isEnabled()) return;
        int max = Math.max(1, properties.getIndexMaxAttempts());
        LocalDateTime now = LocalDateTime.now();
        documentMapper.expireExhausted(now, max);
        for (KnowledgeDocumentEntity candidate : documentMapper.due(now, max)) {
            String token = UUID.randomUUID().toString();
            if (documentMapper.claim(candidate.getId(), token, LocalDateTime.now(), lease(), max) != 1) continue;
            KnowledgeDocumentEntity doc = documentMapper.selectById(candidate.getId());
            if (doc == null) continue;
            process(doc, token, max);
        }
    }

    void process(KnowledgeDocumentEntity doc, String token, int maxAttempts) {
        try {
            KnowledgeBaseEntity base = knowledgeBaseService.requireBase(doc.getKnowledgeBaseId());
            byte[] bytes = fileStorageService.readContent(doc.getFileId()).bytes();
            List<Chunk> chunks = chunk(parser.parse(bytes, doc.getFileName()), base.getChunkSize(), base.getChunkOverlap());
            if (chunks.isEmpty()) throw new IllegalStateException("文档没有可索引的内容");
            progress(doc, token, 0, chunks.size());
            int batchSize = Math.max(1, properties.getEmbeddingBatchSize());
            for (int from = 0; from < chunks.size(); from += batchSize) {
                int to = Math.min(chunks.size(), from + batchSize);
                List<Chunk> batch = chunks.subList(from, to);
                List<EmbeddingService.EmbeddingResult> embeddings = embeddingService.embedBatch(batch.stream().map(Chunk::content).toList());
                if (embeddings.size() != batch.size()) throw new IllegalStateException("向量批次结果数量不匹配");
                // A lease may have expired during a slow provider call. Fence it before writing anything.
                progress(doc, token, from, chunks.size());
                for (int i = 0; i < batch.size(); i++) {
                    Chunk c = batch.get(i);
                    EmbeddingService.EmbeddingResult emb = embeddings.get(i);
                    if (emb.vector() == null || emb.vector().isEmpty()) throw new IllegalStateException("向量结果为空");
                    String metadata = objectMapper.writeValueAsString(Map.of("page", c.page() == null ? 0 : c.page(),
                            "titlePath", c.titlePath() == null ? "" : c.titlePath(), "ordinal", from + i));
                    vectorJdbc.update("INSERT INTO ai_knowledge_chunk_stage(knowledge_base_id,document_id,ordinal,title_path,content,content_hash,metadata,embedding,embedding_model,embedding_dim,index_token) "
                                    + "VALUES(?,?,?,?,?,?,?::jsonb,?::double precision[],?,?,?)",
                            base.getId(), doc.getId(), from + i, c.titlePath(), c.content(),
                            Digests.sha256Hex(c.content().getBytes(StandardCharsets.UTF_8)), metadata,
                            arrayLiteral(emb.vector()), emb.model(), emb.vector().size(), token);
                }
                progress(doc, token, to, chunks.size());
            }
            replace(doc, token, chunks.size());
            knowledgeBaseService.refreshCounts(doc.getKnowledgeBaseId());
        } catch (Exception exception) {
            int attempts = doc.getIndexAttempts() == null ? 1 : doc.getIndexAttempts();
            boolean retry = attempts < maxAttempts;
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            documentMapper.fail(doc.getId(), token, retry ? "QUEUED" : "FAILED",
                    message.substring(0, Math.min(message.length(), 1000)),
                    retry ? LocalDateTime.now().plusMinutes(Math.min(30, 1L << Math.min(attempts - 1, 5))) : null);
            log.warn("知识库索引任务失败，旧索引保留 docId={}, attempt={}: {}", doc.getId(), attempts, message);
        } finally {
            try { vectorJdbc.update("DELETE FROM ai_knowledge_chunk_stage WHERE index_token=?", token); }
            catch (Exception exception) { log.warn("索引暂存分块清理延后: {}", exception.getMessage()); }
        }
    }

    /** The metadata ownership row stays locked across the short PostgreSQL replacement transaction. */
    void replace(KnowledgeDocumentEntity doc, String token, int chunks) {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            if (documentMapper.lockOwned(doc.getId(), token) == null) throw new IllegalStateException("索引任务所有权已变化");
            DataSourceTransactionManager vectorManager = new DataSourceTransactionManager(Objects.requireNonNull(vectorJdbc.getDataSource()));
            new TransactionTemplate(vectorManager).executeWithoutResult(vectorStatus -> {
                Integer staged = vectorJdbc.queryForObject("SELECT count(*) FROM ai_knowledge_chunk_stage WHERE index_token=?", Integer.class, token);
                if (staged == null || staged != chunks) throw new IllegalStateException("暂存索引不完整");
                vectorJdbc.update("DELETE FROM ai_knowledge_chunk WHERE document_id=?", doc.getId());
                vectorJdbc.update("INSERT INTO ai_knowledge_chunk(knowledge_base_id,document_id,ordinal,title_path,content,content_hash,metadata,embedding,embedding_model,embedding_dim) "
                        + "SELECT knowledge_base_id,document_id,ordinal,title_path,content,content_hash,metadata,embedding,embedding_model,embedding_dim "
                        + "FROM ai_knowledge_chunk_stage WHERE index_token=? ORDER BY ordinal", token);
            });
            if (documentMapper.finish(doc.getId(), token, chunks, LocalDateTime.now()) != 1) throw new IllegalStateException("索引任务所有权已变化");
        });
    }

    private void progress(KnowledgeDocumentEntity doc, String token, int processed, int total) {
        if (documentMapper.progress(doc.getId(), token, processed, total, lease()) != 1) throw new IllegalStateException("索引任务所有权已变化");
    }
    private LocalDateTime lease() { return LocalDateTime.now().plusMinutes(Math.max(1, properties.getIndexLeaseMinutes())); }
    private String arrayLiteral(List<Double> values) {
        if (values.stream().anyMatch(value -> value == null || !Double.isFinite(value))) throw new IllegalStateException("向量结果包含无效数值");
        return "{" + values.stream().map(String::valueOf).collect(Collectors.joining(",")) + "}";
    }
    private List<Chunk> chunk(List<KnowledgeDocumentParser.Section> sections, int size, int overlap) {
        int chunkSize = Math.max(1, size);
        int chunkOverlap = Math.max(0, Math.min(overlap, chunkSize - 1));
        List<Chunk> result = new ArrayList<>();
        for (var section : sections) {
            String text = section.text();
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(text.length(), start + chunkSize);
                if (end < text.length()) {
                    int cut = Math.max(text.lastIndexOf('\n', end), text.lastIndexOf('。', end));
                    if (cut > start + chunkSize / 2) end = cut + 1;
                }
                String content = text.substring(start, end).trim();
                if (!content.isBlank()) result.add(new Chunk(section.titlePath(), section.page(), content));
                if (end >= text.length()) break;
                start = Math.max(start + 1, end - chunkOverlap);
            }
        }
        return result;
    }
    private record Chunk(String titlePath, Integer page, String content) {}
}
