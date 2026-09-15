package com.studenthub.modules.rag.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(name = "ragJdbcTemplate")
@RequiredArgsConstructor
public class RagSchemaInitializer {
    @Qualifier("ragJdbcTemplate")
    private final JdbcTemplate jdbc;

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void initialize() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (id BIGSERIAL PRIMARY KEY, knowledge_base_id BIGINT NOT NULL, document_id BIGINT NOT NULL, ordinal INT NOT NULL, title_path TEXT, content TEXT NOT NULL, content_hash VARCHAR(64) NOT NULL, metadata JSONB NOT NULL DEFAULT '{}'::jsonb, embedding DOUBLE PRECISION[], search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(document_id, ordinal))");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ai_kc_base ON ai_knowledge_chunk(knowledge_base_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ai_kc_search ON ai_knowledge_chunk USING GIN(search_vector)");
        log.info("RAG 向量库表已就绪（当前未安装 pgvector 时使用精确余弦 + PostgreSQL 全文检索；安装后可无缝升级 ANN）");
    }
}
