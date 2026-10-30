package com.studenthub.modules.push.controller.dto;

import com.studenthub.modules.push.entity.PushTaskEntity;
import com.studenthub.modules.push.provider.PushChannels;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理端推送任务响应")
public record PushTaskResponse(
        Long id,
        Long campusId,
        String taskName,
        Long templateId,
        String title,
        String content,
        String audienceType,
        String audienceValue,
        List<String> channels,
        String scheduleType,
        LocalDateTime scheduledAt,
        Integer frequencyLimit,
        Integer frequencyWindowHours,
        String status,
        Integer totalCount,
        Integer successCount,
        Integer failedCount,
        Integer deliveredCount,
        Integer clickedCount,
        Integer skippedCount,
        Integer notConfiguredCount,
        Integer retryCount,
        String lastError,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PushTaskResponse from(PushTaskEntity entity) {
        return new PushTaskResponse(
                entity.getId(), entity.getCampusId(), entity.getTaskName(), entity.getTemplateId(),
                entity.getTitle(), entity.getContent(), entity.getAudienceType(), entity.getAudienceValue(),
                PushChannels.parse(entity.getChannels()), entity.getScheduleType(), entity.getScheduledAt(),
                entity.getFrequencyLimit(), entity.getFrequencyWindowHours(), entity.getStatus(),
                entity.getTotalCount(), entity.getSuccessCount(), entity.getFailedCount(),
                entity.getDeliveredCount(), entity.getClickedCount(), entity.getSkippedCount(),
                entity.getNotConfiguredCount(), entity.getRetryCount(), entity.getLastError(),
                entity.getStartedAt(), entity.getFinishedAt(), entity.getRemark(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
