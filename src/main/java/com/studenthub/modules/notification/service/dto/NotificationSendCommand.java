package com.studenthub.modules.notification.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "站内通知发送命令")
public record NotificationSendCommand(
        @Schema(description = "接收人用户 ID") @NotNull Long recipientId,
        @Schema(description = "通知类型：SYSTEM/REQUEST/WORK_ORDER/EVALUATION/COMMUNITY_LIKE/COMMUNITY_COMMENT") @NotBlank String notificationType,
        @Schema(description = "标题") @NotBlank @Size(max = 128) String title,
        @Schema(description = "内容") @NotBlank @Size(max = 512) String content,
        @Schema(description = "关联业务类型", nullable = true) String relatedType,
        @Schema(description = "关联业务 ID", nullable = true) Long relatedId) {
}
