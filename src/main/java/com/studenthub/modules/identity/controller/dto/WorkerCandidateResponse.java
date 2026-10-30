package com.studenthub.modules.identity.controller.dto;

import com.studenthub.common.privacy.PhoneMasker;
import com.studenthub.modules.identity.mapper.WorkerCandidateRow;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "工单派发候选服务人员")
public record WorkerCandidateResponse(
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称") String displayName,
        @Schema(description = "手机号（脱敏）", nullable = true) String phone) {

    public static WorkerCandidateResponse from(WorkerCandidateRow row) {
        return new WorkerCandidateResponse(row.getUserId(), row.getUsername(), row.getDisplayName(),
                PhoneMasker.mask(row.getPhone()));
    }
}
