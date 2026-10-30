package com.studenthub.modules.aichat.controller.dto;

import com.studenthub.modules.aichat.entity.AiSecurityConfigEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "AI 安全策略配置")
public record AiSecurityConfigResponse(
        Long id,
        Long campusId,
        Boolean enabled,
        List<String> sensitiveWords,
        List<String> toolWhitelist,
        Integer rateLimitPerMinute,
        Integer dailyQuotaPerUser,
        Integer contentRetentionDays,
        Boolean retentionEnabled,
        Boolean blockOnHit,
        String remark,
        LocalDateTime updatedAt) {

    public static AiSecurityConfigResponse from(AiSecurityConfigEntity entity) {
        return new AiSecurityConfigResponse(
                entity.getId(), entity.getCampusId(), entity.getEnabled(),
                split(entity.getSensitiveWords()), split(entity.getToolWhitelist()),
                entity.getRateLimitPerMinute(), entity.getDailyQuotaPerUser(),
                entity.getContentRetentionDays(), entity.getRetentionEnabled(), entity.getBlockOnHit(),
                entity.getRemark(), entity.getUpdatedAt());
    }

    private static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.split("[,\\n]")).stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
    }
}
