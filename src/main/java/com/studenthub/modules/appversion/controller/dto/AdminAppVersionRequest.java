package com.studenthub.modules.appversion.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 管理端创建/更新 App 版本（安装包通过单独的原始流接口上传） */
@Schema(description = "App 版本创建/更新请求")
public record AdminAppVersionRequest(
        @Schema(description = "平台：ANDROID / IOS / HARMONY") @NotBlank
        @Pattern(regexp = "ANDROID|IOS|HARMONY", message = "平台仅支持 ANDROID/IOS/HARMONY") String platform,
        @Schema(description = "渠道，默认 OFFICIAL", nullable = true) @Size(max = 32) String channel,
        @Schema(description = "整数版本号（同平台同渠道唯一）") @NotNull @Min(1) Integer versionCode,
        @Schema(description = "展示版本号，如 1.2.0") @NotBlank @Size(max = 32) String versionName,
        @Schema(description = "最低支持版本号：低于该值的客户端强制更新", nullable = true) @Min(0)
        Integer minSupportedVersionCode,
        @Schema(description = "是否强制更新", nullable = true) Boolean forceUpdate,
        @Schema(description = "更新说明", nullable = true) @Size(max = 4000) String updateLog,
        @Schema(description = "外部下载地址（iOS App Store / CDN）；与上传安装包二选一即可发布", nullable = true)
        @Size(max = 512) String downloadUrl) {
}
