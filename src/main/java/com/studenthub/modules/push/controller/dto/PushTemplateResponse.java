package com.studenthub.modules.push.controller.dto;

import com.studenthub.modules.push.entity.PushTemplateEntity;
import com.studenthub.modules.push.provider.PushChannels;
import com.studenthub.modules.push.service.PushTemplateRenderer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理端推送模板响应")
public record PushTemplateResponse(
        Long id,
        Long campusId,
        String templateCode,
        String templateName,
        String scene,
        String titleTemplate,
        String contentTemplate,
        List<String> variables,
        List<String> defaultChannels,
        String status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PushTemplateResponse from(PushTemplateEntity entity) {
        return new PushTemplateResponse(
                entity.getId(),
                entity.getCampusId(),
                entity.getTemplateCode(),
                entity.getTemplateName(),
                entity.getScene(),
                entity.getTitleTemplate(),
                entity.getContentTemplate(),
                PushTemplateRenderer.variables(
                        (entity.getTitleTemplate() == null ? "" : entity.getTitleTemplate())
                                + (entity.getContentTemplate() == null ? "" : entity.getContentTemplate())),
                PushChannels.parse(entity.getDefaultChannels()),
                entity.getStatus(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
