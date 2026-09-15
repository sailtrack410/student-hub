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
    private int retrievalCandidateLimit = 40;
}
