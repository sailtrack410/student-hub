package com.studenthub.modules.aichat.controller.dto;

import com.studenthub.modules.aichat.entity.AiPromptTemplateEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "AI 提示词模板（版本）")
public record AiPromptTemplateResponse(
        Long id,
        Long campusId,
        String promptCode,
        String promptName,
        String scene,
        String systemPrompt,
        String userPromptTemplate,
        List<String> variables,
        Integer versionNo,
        Boolean active,
        String status,
        String changeNote,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AiPromptTemplateResponse from(AiPromptTemplateEntity entity) {
        List<String> variables = entity.getVariables() == null || entity.getVariables().isBlank()
                ? List.of()
                : List.of(entity.getVariables().split(","));
        return new AiPromptTemplateResponse(
                entity.getId(), entity.getCampusId(), entity.getPromptCode(), entity.getPromptName(),
                entity.getScene(), entity.getSystemPrompt(), entity.getUserPromptTemplate(),
                variables, entity.getVersionNo(), entity.getActive(), entity.getStatus(),
                entity.getChangeNote(), entity.getRemark(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
