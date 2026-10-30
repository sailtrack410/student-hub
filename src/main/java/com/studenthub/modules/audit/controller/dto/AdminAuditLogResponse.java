package com.studenthub.modules.audit.controller.dto;

import com.studenthub.modules.audit.entity.SysAuditLogEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "审计日志")
public record AdminAuditLogResponse(
        @Schema(description = "日志 ID") Long id,
        @Schema(description = "操作人 ID") Long operatorId,
        @Schema(description = "操作人") String operatorName,
        @Schema(description = "动作") String action,
        @Schema(description = "资源类型") String resourceType,
        @Schema(description = "资源 ID") String resourceId,
        @Schema(description = "资源名称") String resourceName,
        @Schema(description = "变更前") String beforeJson,
        @Schema(description = "变更后") String afterJson,
        @Schema(description = "结果：SUCCESS/FAILED") String resultStatus,
        @Schema(description = "失败原因") String errorMessage,
        @Schema(description = "IP") String ip,
        @Schema(description = "UA") String userAgent,
        @Schema(description = "traceId") String traceId,
        @Schema(description = "时间") LocalDateTime createdAt) {

    public static AdminAuditLogResponse from(SysAuditLogEntity e) {
        return new AdminAuditLogResponse(e.getId(), e.getOperatorId(), e.getOperatorName(), e.getAction(),
                e.getResourceType(), e.getResourceId(), e.getResourceName(), e.getBeforeJson(), e.getAfterJson(),
                e.getResultStatus() == null ? SysAuditLogEntity.RESULT_SUCCESS : e.getResultStatus(),
                e.getErrorMessage(), e.getIp(), e.getUserAgent(), e.getTraceId(), e.getCreatedAt());
    }
}
