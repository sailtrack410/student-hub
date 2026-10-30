package com.studenthub.modules.rag.service;

import com.studenthub.modules.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 管理端 RAG 连通性测试：只读探测 PostgreSQL 与向量表状态，不返回连接密码。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagConnectivityProbe {

    @Qualifier("ragJdbcTemplate")
    private final JdbcTemplate jdbc;
    private final RagProperties properties;

    public ProbeResult test() {
        if (!properties.isEnabled()) {
            return new ProbeResult(false, 0, false, 0, null, "RAG 未启用");
        }
        long start = System.currentTimeMillis();
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            long chunks = 0;
            String model = null;
            try {
                Long count = jdbc.queryForObject("SELECT count(*) FROM ai_knowledge_chunk", Long.class);
                chunks = count == null ? 0 : count;
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT embedding_model, count(*) AS c FROM ai_knowledge_chunk "
                                + "WHERE embedding IS NOT NULL AND embedding_model IS NOT NULL "
                                + "GROUP BY embedding_model ORDER BY c DESC");
                model = rows.stream().map(row -> String.valueOf(row.get("embedding_model")))
                        .distinct().collect(Collectors.joining(", "));
            } catch (Exception tableMissing) {
                log.debug("RAG 向量表尚未初始化: {}", tableMissing.getMessage());
            }
            String detail = "PostgreSQL 可达，知识库向量分块 " + chunks + " 个"
                    + (model == null || model.isBlank() ? "；尚未索引向量" : "；向量模型：" + model);
            return new ProbeResult(true, System.currentTimeMillis() - start, true, chunks, model, detail);
        } catch (Exception exception) {
            return new ProbeResult(false, System.currentTimeMillis() - start, true, 0, null,
                    "PostgreSQL 不可达：" + safeMessage(exception));
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String cleaned = message.replaceAll("[\\r\\n]+", " ").trim();
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            cleaned = cleaned.replace(properties.getPassword(), "******");
        }
        return cleaned.length() > 180 ? cleaned.substring(0, 180) + "…" : cleaned;
    }

    public record ProbeResult(boolean reachable, long latencyMs, boolean enabled,
                              long vectorChunks, String embeddingModel, String detail) {
    }
}
