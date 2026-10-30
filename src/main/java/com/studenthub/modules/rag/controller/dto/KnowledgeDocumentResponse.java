package com.studenthub.modules.rag.controller.dto;
import com.studenthub.modules.rag.entity.KnowledgeDocumentEntity;
import java.time.LocalDateTime;
public record KnowledgeDocumentResponse(Long id, Long knowledgeBaseId, String fileName, String contentType,
        Long fileSize, String status, Integer chunkCount, String errorMessage, LocalDateTime indexedAt, LocalDateTime createdAt, Integer indexAttempts, Integer processedChunks, Integer totalChunks, LocalDateTime nextAttemptAt) {
    public static KnowledgeDocumentResponse from(KnowledgeDocumentEntity e) { return new KnowledgeDocumentResponse(e.getId(),e.getKnowledgeBaseId(),e.getFileName(),e.getContentType(),e.getFileSize(),e.getStatus(),e.getChunkCount(),e.getErrorMessage(),e.getIndexedAt(),e.getCreatedAt(),e.getIndexAttempts(),e.getProcessedChunks(),e.getTotalChunks(),e.getNextAttemptAt()); }
}
