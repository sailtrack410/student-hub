package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理端话题新建/更新请求 */
@Schema(description = "社区话题新建/更新请求")
public record AdminCommunityTopicRequest(
        @Schema(description = "话题名称") @NotBlank @Size(max = 32) String name,
        @Schema(description = "热度值（排序用）") Integer heat,
        @Schema(description = "状态：ENABLED/DISABLED") String status) {
}
