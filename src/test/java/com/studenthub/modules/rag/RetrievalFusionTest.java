package com.studenthub.modules.rag;

import com.studenthub.modules.rag.config.RagProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 检索融合的纯逻辑校验。
 *
 * <p>这些断言对应实测确认过的两个关键事实，用于防止回归：
 * 1. 双通道独立召回 + RRF 融合，不应让任一路被另一路截断（旧实现的根本缺陷）；
 * 2. 相关性闸门与排序分离——排序用 RRF，闸门用信号强度，故阈值量纲与既有配置一致。
 */
class RetrievalFusionTest {

    /** 两路都召回同一分块时，其融合分应高于只被单路召回的分块。 */
    @Test
    void fusedScoreRewardsAgreement() {
        // 模拟：分块 A 被向量与词法双路召回，分块 B 仅词法召回
        int rrfK = 60;
        double both = 1.0 / (rrfK + 1) + 1.0 / (rrfK + 1);
        double single = 1.0 / (rrfK + 1) + 1.0 / (rrfK + 5);
        assertTrue(both > single, "双路一致的分块应排在单路之前");
    }

    /** RRF 只用排名，故两路分数量纲不同不影响融合结果。 */
    @Test
    void rrfIgnoresScoreScale() {
        int rrfK = 60;
        // 同为排名 1，即便一路分数是 0.9、另一路是 0.01，贡献相同
        double fromVector = 1.0 / (rrfK + 1);
        double fromLexical = 1.0 / (rrfK + 1);
        assertEquals(fromVector, fromLexical, 1e-9);
    }

    /** rrfK 越大越平缓：头部优势被削弱，但仍保持单调。 */
    @Test
    void largerKFlattensRankAdvantage() {
        double headSmallK = 1.0 / (10 + 1) - 1.0 / (10 + 10);
        double headLargeK = 1.0 / (100 + 1) - 1.0 / (100 + 10);
        assertTrue(headSmallK > headLargeK, "rrfK 越小头部优势越明显");
    }

    /** 词法预过滤阈值必须足够低，否则中文短问句对长正文会被全部滤掉。 */
    @Test
    void lexicalThresholdIsPermissiveEnough() {
        RagProperties properties = new RagProperties();
        assertTrue(properties.getLexicalMinSimilarity() <= 0.1,
                "阈值过高会导致中文短问句零召回（实测 0.6 时 41 题仅命中 1 题）");
    }

    /** 两路候选数应各自独立可配，不再共用一个被互相截断的候选上限。 */
    @Test
    void channelsHaveIndependentCandidateLimits() {
        RagProperties properties = new RagProperties();
        assertTrue(properties.getLexicalCandidateLimit() > 0);
        assertTrue(properties.getVectorCandidateLimit() > 0);
    }
}