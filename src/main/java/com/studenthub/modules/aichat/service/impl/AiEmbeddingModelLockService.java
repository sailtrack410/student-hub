package com.studenthub.modules.aichat.service.impl;

import com.studenthub.modules.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库向量模型锁定检查。
 *
 * <p>一旦 RAG 向量表里已有分块，切换默认 EMBEDDING 模型会造成维度/语义不一致，
 * 因此禁止在管理端更换、清除、停用或删除当前嵌入模型。只有删除全部知识库文档
 * （向量分块清空）后才自动解锁。RAG 未启用或数据库暂不可达时无法读取向量状态，
 * 此时不阻塞 AI 配置页，但会在默认模型页提示“暂不检查”；恢复后自动重新锁定。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiEmbeddingModelLockService {

    @Qualifier("ragJdbcTemplate")
    private final JdbcTemplate ragJdbcTemplate;
    private final RagProperties ragProperties;

    public LockState current() {
        if (!ragProperties.isEnabled()) {
            return LockState.unlocked("RAG 未启用，暂不检查向量模型");
        }
        try {
            Long chunks = ragJdbcTemplate.queryForObject(
                    "SELECT count(*) FROM ai_knowledge_chunk WHERE embedding IS NOT NULL", Long.class);
            long count = chunks == null ? 0 : chunks;
            if (count <= 0) {
                return LockState.unlocked("知识库尚无向量，选择后可更换");
            }
            List<Map<String, Object>> rows = ragJdbcTemplate.queryForList(
                    "SELECT embedding_model, count(*) AS c FROM ai_knowledge_chunk "
                            + "WHERE embedding IS NOT NULL AND embedding_model IS NOT NULL "
                            + "GROUP BY embedding_model ORDER BY c DESC");
            String models = rows.stream()
                    .map(row -> String.valueOf(row.get("embedding_model")))
                    .filter(model -> !model.isBlank() && !"null".equals(model))
                    .distinct()
                    .collect(Collectors.joining(" / "));
            if (models.isBlank()) {
                return new LockState(true, "", count,
                        "知识库已有 " + count + " 个向量分块，但未记录嵌入模型；为避免维度不一致，嵌入模型不可更改");
            }
            return new LockState(true, models, count,
                    "知识库已有 " + count + " 个向量分块，使用的嵌入模型为「" + models
                            + "」；为保证向量维度与语义一致，嵌入模型不可更改。"
                            + "如需更换，请先删除全部知识库文档，待向量清空后再修改");
        } catch (Exception exception) {
            // 表不存在/数据库暂不可达时不阻塞 AI 配置页，避免 RAG 故障连带管理端不可用；
            // 恢复连接后下一次读取会重新锁定。
            log.debug("读取知识库向量模型锁定状态失败: {}", exception.getMessage());
            return LockState.unlocked("暂时无法读取知识库向量状态（RAG 数据库不可达）");
        }
    }

    public record LockState(boolean locked, String model, long vectorChunks, String message) {
        public static LockState unlocked(String message) {
            return new LockState(false, null, 0, message);
        }
    }
}
