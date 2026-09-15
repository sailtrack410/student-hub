package com.studenthub.modules.rag.controller.dto;
import com.studenthub.modules.rag.entity.KnowledgeBaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record KnowledgeBaseResponse(Long id, String name, String description, String visibility, Boolean enabled,
        Integer chunkSize, Integer chunkOverlap, Integer topK, BigDecimal scoreThreshold, String systemPrompt,
        Integer documentCount, Integer chunkCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static KnowledgeBaseResponse from(KnowledgeBaseEntity e) { return new KnowledgeBaseResponse(e.getId(),e.getName(),e.getDescription(),e.getVisibility(),e.getEnabled(),e.getChunkSize(),e.getChunkOverlap(),e.getTopK(),e.getScoreThreshold(),e.getSystemPrompt(),e.getDocumentCount(),e.getChunkCount(),e.getCreatedAt(),e.getUpdatedAt()); }
}
