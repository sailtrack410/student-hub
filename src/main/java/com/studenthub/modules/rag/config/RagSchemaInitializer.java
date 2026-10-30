package com.studenthub.modules.rag.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagSchemaInitializer {
    @Qualifier("ragJdbcTemplate")
    private final JdbcTemplate jdbc;
    private final RagProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        initializeIfEnabled();
    }

    /** RAG 启用时确保向量表/索引存在；管理端启用或切换数据库后也会调用。 */
    public synchronized void initializeIfEnabled() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            doInitialize();
        } catch (Exception exception) {
            log.warn("RAG 向量库初始化失败（RAG 可能暂不可用）: {}", exception.getMessage());
        }
    }

    private void doInitialize() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_knowledge_chunk ("
                + "id BIGSERIAL PRIMARY KEY, "
                + "knowledge_base_id BIGINT NOT NULL, "
                + "document_id BIGINT NOT NULL, "
                + "ordinal INT NOT NULL, "
                + "title_path TEXT, "
                + "content TEXT NOT NULL, "
                + "content_hash VARCHAR(64) NOT NULL, "
                + "metadata JSONB NOT NULL DEFAULT '{}'::jsonb, "
                + "embedding DOUBLE PRECISION[], "
                + "embedding_model VARCHAR(128), "
                + "embedding_dim INT, "
                + "created_at TIMESTAMPTZ NOT NULL DEFAULT now(), "
                + "UNIQUE(document_id, ordinal))");
        // 维度与模型契约：换向量模型后据此识别并提示重建，避免余弦静默退化为 0
        jdbc.execute("ALTER TABLE ai_knowledge_chunk ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(128)");
        jdbc.execute("ALTER TABLE ai_knowledge_chunk ADD COLUMN IF NOT EXISTS embedding_dim INT");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ai_kc_base ON ai_knowledge_chunk(knowledge_base_id)");
        // 中文词法：pg_trgm 三元组，无需词典；simple 分词不切分中文，全文检索对中文恒为空
        try {
            jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ai_kc_content_trgm "
                    + "ON ai_knowledge_chunk USING GIN (content gin_trgm_ops)");
            log.info("RAG 词法索引已就绪（pg_trgm 三元组，支持中文子串匹配）");
        } catch (Exception e) {
            log.warn("pg_trgm 扩展不可用，词法召回将退化为全表扫描：{}", e.getMessage());
        }
        // Reindex builds a private generation; readers only see the active table.
        jdbc.execute("DROP INDEX IF EXISTS uk_ai_kc_content");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ai_kc_content_hash ON ai_knowledge_chunk(knowledge_base_id, content_hash)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_knowledge_chunk_stage (LIKE ai_knowledge_chunk INCLUDING DEFAULTS)");
        jdbc.execute("ALTER TABLE ai_knowledge_chunk_stage ADD COLUMN IF NOT EXISTS index_token VARCHAR(36)");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_kc_stage_job ON ai_knowledge_chunk_stage(index_token, ordinal)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ai_kc_stage_doc ON ai_knowledge_chunk_stage(document_id)");
        log.info("RAG 向量库表已就绪（未安装 pgvector 时使用精确余弦 + pg_trgm 词法混合召回）");
    }
}