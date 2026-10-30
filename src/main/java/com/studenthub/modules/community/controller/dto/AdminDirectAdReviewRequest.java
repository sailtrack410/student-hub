package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "官方直投广告审核")
public record AdminDirectAdReviewRequest(
        @Schema(description = "审核动作：APPROVE（通过）/REJECT（驳回）")
        @NotBlank @Size(max = 16) String action,
        @Schema(description = "驳回原因，REJECT 时必填") @Size(max = 200) String reason) {
}
