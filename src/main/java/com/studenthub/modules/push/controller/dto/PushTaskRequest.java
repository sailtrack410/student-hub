package com.studenthub.modules.push.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "管理端推送任务创建/更新")
public record PushTaskRequest(
        @Schema(description = "任务名称") @NotBlank @Size(max = 100) String taskName,
        @Schema(description = "关联模板 ID（可选，用于追溯；标题/正文仍以本请求为准）") Long templateId,
        @Schema(description = "标题，支持 {变量}") @NotBlank @Size(max = 200) String title,
        @Schema(description = "正文，支持 {变量}") @NotBlank @Size(max = 2000) String content,
        @Schema(description = "模板变量取值") Map<String, String> variables,
        @Schema(description = "人群：ALL/ROLE/ORGANIZATION/USER") @NotBlank String audienceType,
        @Schema(description = "人群参数：ROLE/ORGANIZATION 为 ID；USER 为逗号分隔用户 ID") @Size(max = 1000) String audienceValue,
        @Schema(description = "渠道：LOG/WS/FCM/APNS/WEBPUSH，逗号分隔或数组") List<String> channels,
        @Schema(description = "发送时机：NOW/SCHEDULED") @NotBlank String scheduleType,
        @Schema(description = "定时发送时间（scheduleType=SCHEDULED 时必填）") LocalDateTime scheduledAt,
        @Schema(description = "频控：同一用户在窗口内最多接收次数，0 不限制") @Min(0) @Max(100) Integer frequencyLimit,
        @Schema(description = "频控窗口小时数，0 不限制") @Min(0) @Max(720) Integer frequencyWindowHours,
        @Schema(description = "创建动作：DRAFT（存草稿）/SEND（立即发送）/SCHEDULE（定时）") @NotBlank String action,
        @Schema(description = "备注") @Size(max = 500) String remark) {
}
