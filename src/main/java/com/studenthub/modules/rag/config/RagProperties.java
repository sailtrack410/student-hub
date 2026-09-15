package com.studenthub.modules.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studenthub.rag")
public class RagProperties {
    private boolean enabled;
    private String jdbcUrl;
    private String username;
    private String password;
    private int maxDocumentSizeMb = 30;
    private int maxParsedCharacters = 2_000_000;
    private int embeddingBatchSize = 16;
    /** 词法召回候选数（向量与词法各自独立召回，不再互相截断）。 */
    private int lexicalCandidateLimit = 40;
    /** 向量召回候选数。 */
    private int vectorCandidateLimit = 40;
    /** pg_trgm word_similarity 预过滤下界；过高会漏召回，过低会放大候选。 */
    private double lexicalMinSimilarity = 0.05;
    /** RRF 融合常数，越小越强调头部排名。 */
    private int rrfK = 60;
}
