package com.studenthub.modules.evaluation.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "管理端回复服务评价")
public record AdminReplyRequest(
        @Schema(description = "回复内容") @NotBlank(message = "回复内容必填")
        @Size(max = 512, message = "回复最长 512 字") String reply
) {
}
