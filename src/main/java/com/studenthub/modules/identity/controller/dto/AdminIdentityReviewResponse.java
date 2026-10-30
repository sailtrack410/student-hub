package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理端身份审核详情：申请人、材料、审核历史与当前状态")
public record AdminIdentityReviewResponse(
        Long userId,
        String username,
        String displayName,
        String studentNo,
        String college,
        @Schema(description = "手机号（脱敏）") String phone,
        String identityType,
        String authorizationStatus,
        @Schema(description = "最近一次审核备注", nullable = true) String authorizationNote,
        LocalDateTime submittedAt,
        Long reviewedBy,
        String reviewerName,
        LocalDateTime reviewedAt,
        @Schema(description = "申请材料（空列表表示未上传，页面不得显示占位图）") List<IdentityEvidenceResponse> materials,
        @Schema(description = "审核历史，按时间倒序") List<IdentityReviewTimelineEntry> timeline,
        @Schema(description = "可用的拒绝原因模板（平台内置 + 本校区自定义，仅启用）") List<IdentityRejectReasonResponse> rejectReasons) {
}
