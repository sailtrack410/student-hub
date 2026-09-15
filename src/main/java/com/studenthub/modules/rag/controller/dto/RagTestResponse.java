package com.studenthub.modules.rag.controller.dto;
import java.util.List;
public record RagTestResponse(String answer, List<RagHit> hits, long retrievalMs, long generationMs, String embeddingModel) {
    public record RagHit(Long chunkId, Long documentId, String fileName, Integer ordinal, Integer page,
                         String titlePath, String content, double score, double vectorScore, double keywordScore) {}
}
