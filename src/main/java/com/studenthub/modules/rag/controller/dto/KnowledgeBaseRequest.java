package com.studenthub.modules.rag.controller.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record KnowledgeBaseRequest(
        @NotBlank @Size(max=100) String name,
        @Size(max=500) String description,
        @Pattern(regexp="^(INTERNAL|PUBLIC)$") String visibility,
        Boolean enabled,
        @Min(300) @Max(3000) Integer chunkSize,
        @Min(0) @Max(600) Integer chunkOverlap,
        @Min(1) @Max(20) Integer topK,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal scoreThreshold,
        @Size(max=1000) String systemPrompt) {}
