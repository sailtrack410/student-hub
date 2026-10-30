package com.studenthub.modules.push.content.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "创作者认证审核")
public record CreatorCertifyRequest(
        @Schema(description = "决定：APPROVED/REJECTED/PENDING/DISABLED") @NotBlank String decision,
        @Schema(description = "审核备注/驳回原因") @Size(max = 255) String note) {
}
