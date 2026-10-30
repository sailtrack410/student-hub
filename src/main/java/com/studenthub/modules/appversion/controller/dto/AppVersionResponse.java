package com.studenthub.modules.appversion.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 管理端版本列表项 */
@Schema(description = "App 版本（管理端）")
public record AppVersionResponse(
        Long id,
        String platform,
        String channel,
        Integer versionCode,
        String versionName,
        Integer minSupportedVersionCode,
        Boolean forceUpdate,
        String updateLog,
        String packageFileName,
        Long packageSize,
        String packageSha256,
        boolean hasPackage,
        String downloadUrl,
        String status,
        LocalDateTime publishedAt,
        Long downloadCount,
        LocalDateTime createdAt) {
}
