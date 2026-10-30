package com.studenthub.modules.appversion.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** App 启动更新检查结果（公开接口） */
@Schema(description = "App 更新检查结果")
public record AppVersionCheckResponse(
        @Schema(description = "是否有新版本") boolean hasUpdate,
        @Schema(description = "是否强制更新（版本自带强更或低于最低支持版本）") boolean forceUpdate,
        @Schema(description = "客户端当前版本号") Integer currentVersionCode,
        @Schema(description = "当前已发布的最新版本；无发布版本时为 null", nullable = true) Latest latest) {

    /** 最新版本信息 */
    @Schema(description = "最新版本信息")
    public record Latest(
            Long id,
            Integer versionCode,
            String versionName,
            Integer minSupportedVersionCode,
            Boolean forceUpdate,
            String updateLog,
            String downloadUrl,
            Long packageSize,
            String packageSha256,
            LocalDateTime publishedAt) {
    }
}
