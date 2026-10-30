package com.studenthub.modules.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.rag.config.RagProperties;
import com.studenthub.modules.rag.controller.dto.RagTestResponse;
import com.studenthub.modules.rag.entity.KnowledgeDocumentEntity;
import com.studenthub.modules.rag.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识库混合检索。
 *
 * <p>向量召回与词法召回各自独立取候选，再用 RRF 融合排序。旧实现先按全文检索取 N 条候选、
 * 再在候选内算向量余弦，导致关键词没召回的正确答案即使向量再准也进不了排序；又因中文在
 * simple 分词下不被切分，该路径对中文恒为空，候选池实际退化为"无排序的任意 N 条"。
 *
 * <p>词法通道使用 pg_trgm 三元组（无需中文词典，可走 GIN 索引）；向量通道在未安装 pgvector
 * 时用 SQL 侧精确余弦。融合采用 RRF，只用排名不用分数，因此两路分数量纲不同也不影响，
 * 换向量模型后也无需重新标定权重。
 *
 * <p>职责分工：<b>排序由 RRF 决定</b>，<b>相关性闸门由信号强度决定</b>。RRF 分数随融合规模
 * 变化，不适合直接与绝对阈值比较，故对外 score 输出归一化 RRF（与排序一致），阈值过滤则
 * 用两通道的原始信号强度，使既有 scoreThreshold 配置保持原有量纲语义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {
    private final RagProperties properties;
    private final KnowledgeDocumentMapper documentMapper;
    private final ObjectMapper objectMapper;
    @Qualifier("ragJdbcTemplate")
    private final JdbcTemplate jdbc;

    /** 一个候选分块及其在两通道内的信号与排名。 */
    private record Candidate(long chunkId, long documentId, int ordinal, String titlePath,
                             String content, String metadata, Double vectorScore,
                             Double lexicalScore, Integer vectorRank, Integer lexicalRank,
                             double rrf) {
    }

    /**
     * 混合检索。向量通道不可用（未配置向量模型或调用失败）时自动降级为纯词法召回。
     *
     * @param queryVector 查询向量，可为 null
     * @param threshold   相关性闸门，按两通道最大信号强度过滤；null 表示不过滤
     */
    public List<RagTestResponse.RagHit> retrieve(List<Long> baseIds, String question,
                                                List<Double> queryVector, int topK,
                                                java.math.BigDecimal threshold) {
        if (baseIds == null || baseIds.isEmpty() || question == null || question.isBlank()) {
            return List.of();
        }
        int lexicalLimit = Math.max(topK, properties.getLexicalCandidateLimit());
        int vectorLimit = Math.max(topK, properties.getVectorCandidateLimit());

        List<Candidate> lexical = recallLexical(baseIds, question, lexicalLimit);
        List<Candidate> vector = (queryVector == null || queryVector.isEmpty())
                ? List.of()
                : recallVector(baseIds, queryVector, vectorLimit);

        List<Candidate> fused = fuse(lexical, vector, properties.getRrfK());
        Map<Long, String> names = documentNames(baseIds);

        List<RagTestResponse.RagHit> hits = new ArrayList<>();
        for (Candidate c : fused) {
            double signal = signalStrength(c);
            if (threshold != null && signal < threshold.doubleValue()) {
                continue;
            }
            hits.add(new RagTestResponse.RagHit(
                    c.chunkId(), c.documentId(),
                    names.getOrDefault(c.documentId(), "未知文档"),
                    c.ordinal(), metadataPage(c.metadata()), c.titlePath(), c.content(),
                    c.rrf(),
                    c.vectorScore() == null ? 0.0 : c.vectorScore(),
                    c.lexicalScore() == null ? 0.0 : c.lexicalScore(),
                    c.vectorRank(), c.lexicalRank()));
        }
        return hits.stream().limit(topK).toList();
    }

    /** 相关性信号：取两通道原始分的较大者，量纲与既有阈值配置一致。 */
    private double signalStrength(Candidate c) {
        double vector = c.vectorScore() == null ? 0.0 : c.vectorScore();
        double lexical = c.lexicalScore() == null ? 0.0 : c.lexicalScore();
        return Math.max(vector, lexical);
    }

    /**
     * 词法召回：pg_trgm 三元组相似度，正文与标题路径取较大者。
     * 阈值下界用于让 GIN 索引生效（过低会放大候选，过高会漏召回）。
     */
    private List<Candidate> recallLexical(List<Long> baseIds, String question, int limit) {
        String sql = "SELECT id, document_id, ordinal, title_path, content, metadata, "
                + "greatest(word_similarity(?, content), word_similarity(?, coalesce(title_path, ''))) AS lexical "
                + "FROM ai_knowledge_chunk WHERE knowledge_base_id IN (" + marks(baseIds.size()) + ") "
                + "AND greatest(word_similarity(?, content), word_similarity(?, coalesce(title_path, ''))) >= ? "
                + "ORDER BY lexical DESC LIMIT " + limit;
        List<Object> params = new ArrayList<>();
        params.add(question);
        params.add(question);
        params.addAll(baseIds);
        params.add(question);
        params.add(question);
        params.add(properties.getLexicalMinSimilarity());
        try {
            return toCandidates(jdbc.queryForList(sql, params.toArray()));
        } catch (Exception e) {
            log.warn("词法召回失败，本轮仅依赖向量通道：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 向量召回：SQL 侧精确余弦，并按维度过滤。
     *
     * <p>维度必须显式过滤：PostgreSQL 的 unnest 在两数组长度不等时静默截断而非报错，
     * 若不校验，换过向量模型的历史分块会被算成错误分数并与新分块混排。
     */
    private List<Candidate> recallVector(List<Long> baseIds, List<Double> queryVector, int limit) {
        String literal = arrayLiteral(queryVector);
        int dim = queryVector.size();
        String sql = "SELECT id, document_id, ordinal, title_path, content, metadata, "
                + "((SELECT sum(x * y) FROM unnest(embedding, " + literal + "::double precision[]) t(x, y)) "
                + "/ NULLIF(sqrt((SELECT sum(x * x) FROM unnest(embedding) x)) "
                + "* sqrt((SELECT sum(y * y) FROM unnest(" + literal + "::double precision[]) y)), 0)) AS vector "
                + "FROM ai_knowledge_chunk WHERE knowledge_base_id IN (" + marks(baseIds.size()) + ") "
                + "AND array_length(embedding, 1) = ? "
                + "ORDER BY vector DESC NULLS LAST LIMIT " + limit;
        List<Object> params = new ArrayList<>(baseIds);
        params.add(dim);
        try {
            List<Candidate> result = toCandidates(jdbc.queryForList(sql, params.toArray()));
            warnOnDimensionMismatch(baseIds, dim);
            return result;
        } catch (Exception e) {
            log.warn("向量召回失败，本轮仅依赖词法通道：{}", e.getMessage());
            return List.of();
        }
    }

    private void warnOnDimensionMismatch(List<Long> baseIds, int dim) {
        try {
            List<Object> params = new ArrayList<>(baseIds);
            params.add(dim);
            Long mismatched = jdbc.queryForObject(
                    "SELECT count(*) FROM ai_knowledge_chunk WHERE knowledge_base_id IN ("
                            + marks(baseIds.size()) + ") AND embedding IS NOT NULL AND array_length(embedding, 1) <> ?",
                    Long.class, params.toArray());
            if (mismatched != null && mismatched > 0) {
                log.warn("检测到 {} 个分块的向量维度与当前模型（{} 维）不一致，已跳过参与向量召回；"
                        + "如更换过向量模型，请在知识库中重建索引", mismatched, dim);
            }
        } catch (Exception e) {
            log.debug("维度一致性检查失败：{}", e.getMessage());
        }
    }

    /** RRF 融合：按各路排名倒数求和，两路分数无需同量纲。 */
    private List<Candidate> fuse(List<Candidate> lexical, List<Candidate> vector, int rrfK) {
        Map<Long, Candidate> merged = new LinkedHashMap<>();
        for (Candidate c : vector) {
            merged.put(c.chunkId(), c);
        }
        for (Candidate c : lexical) {
            merged.merge(c.chunkId(), c, (inVector, inLexical) -> new Candidate(
                    inVector.chunkId(), inVector.documentId(), inVector.ordinal(),
                    inVector.titlePath() != null ? inVector.titlePath() : inLexical.titlePath(),
                    inVector.content(), inVector.metadata(),
                    inVector.vectorScore(), inLexical.lexicalScore(),
                    inVector.vectorRank(), inLexical.lexicalRank(), 0));
        }
        // 理论最大 RRF：两路均排第一。用于归一化到 0~1，使对外分数与排序单调一致。
        double best = 2.0 / (rrfK + 1);
        List<Candidate> scored = new ArrayList<>(merged.size());
        for (Candidate c : merged.values()) {
            double rrf = 0;
            if (c.vectorRank() != null) {
                rrf += 1.0 / (rrfK + c.vectorRank());
            }
            if (c.lexicalRank() != null) {
                rrf += 1.0 / (rrfK + c.lexicalRank());
            }
            scored.add(new Candidate(c.chunkId(), c.documentId(), c.ordinal(), c.titlePath(),
                    c.content(), c.metadata(), c.vectorScore(), c.lexicalScore(),
                    c.vectorRank(), c.lexicalRank(), Math.min(1.0, rrf / best)));
        }
        scored.sort(java.util.Comparator.comparingDouble(Candidate::rrf).reversed());
        return scored;
    }

    private List<Candidate> toCandidates(List<Map<String, Object>> rows) {
        List<Candidate> result = new ArrayList<>(rows.size());
        int rank = 1;
        for (Map<String, Object> row : rows) {
            result.add(new Candidate(
                    ((Number) row.get("id")).longValue(),
                    ((Number) row.get("document_id")).longValue(),
                    ((Number) row.get("ordinal")).intValue(),
                    (String) row.get("title_path"),
                    String.valueOf(row.get("content")),
                    row.get("metadata") == null ? "{}" : String.valueOf(row.get("metadata")),
                    numberOrNull(row.get("vector")),
                    numberOrNull(row.get("lexical")),
                    row.get("vector") != null ? rank : null,
                    row.get("lexical") != null ? rank : null,
                    0));
            rank++;
        }
        return result;
    }

    private Map<Long, String> documentNames(List<Long> baseIds) {
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .in(KnowledgeDocumentEntity::getKnowledgeBaseId, baseIds))
                .stream().collect(Collectors.toMap(KnowledgeDocumentEntity::getId,
                        KnowledgeDocumentEntity::getFileName, (a, b) -> a));
    }

    private Integer metadataPage(String metadata) {
        try {
            int page = objectMapper.readTree(metadata == null ? "{}" : metadata).path("page").asInt(0);
            return page > 0 ? page : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String marks(int count) {
        return count <= 0 ? "NULL" : String.join(",", Collections.nCopies(count, "?"));
    }

    private Double numberOrNull(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    /** PostgreSQL 数组字面量（必须带单引号再 cast），如 '{a,b,c}'。 */
    private String arrayLiteral(List<Double> vector) {
        return "'{" + vector.stream()
                .map(v -> Double.toString(v == null ? 0 : v))
                .collect(Collectors.joining(",")) + "}'";
    }
}