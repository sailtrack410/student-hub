package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 官方号运营：官方账号代发社区帖子（C12）。 */
@Schema(description = "官方账号代发社区帖子请求")
public record AdminOfficialPublishRequest(
        @Schema(description = "官方账号用户 ID") @NotNull Long officialUserId,
        @Schema(description = "目标分区 ID") @NotNull Long sectionId,
        @Schema(description = "标题（≤80 字）") @NotBlank @Size(max = 80) String title,
        @Schema(description = "正文（≤2000 字）") @NotBlank @Size(max = 2000) String content,
        @Schema(description = "图片文件 ID（≤9 张，可为空）") @Size(max = 9) List<Long> imageFileIds,
        @Schema(description = "话题标签（≤8 个，可为空）") @Size(max = 8) List<@Size(max = 32) String> topics) {
}
