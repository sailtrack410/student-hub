package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "更新个人资料请求（昵称与头像至少提供一项）")
public record UpdateProfileRequest(
        @Schema(description = "昵称", nullable = true) @Size(min = 1, max = 32) String displayName,
        @Schema(description = "头像文件 ID（先经 /api/app/files 上传）", nullable = true) Long avatarFileId) {
}
