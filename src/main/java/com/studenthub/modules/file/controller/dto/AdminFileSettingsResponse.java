package com.studenthub.modules.file.controller.dto;

import com.studenthub.modules.systemconfig.controller.dto.AdminConfigItemResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "文件存储设置与容量统计")
public record AdminFileSettingsResponse(
        @Schema(description = "当前运行时存储实现") String storage,
        @Schema(description = "当前运行时单文件上限 MB") int maxSizeMb,
        @Schema(description = "当前运行时签名有效期（分钟）") int viewTtlMinutes,
        @Schema(description = "签名密钥是否已从默认值替换") boolean signSecretConfigured,
        @Schema(description = "本地存储目录") String storageDir,
        @Schema(description = "MinIO 端点") String minioEndpoint,
        @Schema(description = "MinIO 对外公开地址") String minioPublicEndpoint,
        @Schema(description = "MinIO 桶") String minioBucket,
        @Schema(description = "MinIO AccessKey 是否已配置") boolean minioAccessKeyConfigured,
        @Schema(description = "MinIO SecretKey 是否已配置") boolean minioSecretKeyConfigured,
        @Schema(description = "OSS Endpoint") String ossEndpoint,
        @Schema(description = "OSS 对外公开地址") String ossPublicEndpoint,
        @Schema(description = "OSS 桶") String ossBucket,
        @Schema(description = "OSS AccessKeyId 是否已配置") boolean ossAccessKeyIdConfigured,
        @Schema(description = "OSS AccessKeySecret 是否已配置") boolean ossAccessKeySecretConfigured,
        @Schema(description = "文件总数") long totalFiles,
        @Schema(description = "文件总字节数") long totalBytes,
        @Schema(description = "按内容类型统计") List<TypeStat> byType,
        @Schema(description = "磁盘总空间字节") long diskTotalBytes,
        @Schema(description = "磁盘可用字节") long diskUsableBytes,
        @Schema(description = "配置生效说明") String runtimeNote,
        @Schema(description = "MIME/扩展名白名单口径") String mimePolicy,
        @Schema(description = "配置中心 file 分组配置项") List<AdminConfigItemResponse> configItems) {

    public record TypeStat(String contentType, long count, long bytes) {
    }
}
