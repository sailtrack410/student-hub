package com.studenthub.modules.file.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "文件存储设置（写入 sys_config file 分组；保存后运行时热更新，无需重启）")
public record AdminFileSettingsRequest(
        @Schema(description = "单文件大小上限 MB") @Min(1) @Max(1024) Integer maxSizeMb,
        @Schema(description = "签名访问有效期（分钟）") @Min(1) @Max(1440) Integer viewTtlMinutes,
        @Schema(description = "存储实现：local / minio / oss") @Size(max = 16) String storage,
        @Schema(description = "本地存储目录") @Size(max = 255) String storageDir,
        @Schema(description = "短时签名 HMAC 密钥；留空不修改") @Size(max = 128) String signSecret,
        @Schema(description = "MinIO 后端访问端点") @Size(max = 255) String minioEndpoint,
        @Schema(description = "MinIO 对外公开地址；留空使用访问端点") @Size(max = 255) String minioPublicEndpoint,
        @Schema(description = "MinIO 桶名称") @Size(max = 64) String minioBucket,
        @Schema(description = "MinIO AccessKey；留空不修改") @Size(max = 255) String minioAccessKey,
        @Schema(description = "MinIO SecretKey；留空不修改") @Size(max = 255) String minioSecretKey,
        @Schema(description = "阿里云 OSS Endpoint") @Size(max = 255) String ossEndpoint,
        @Schema(description = "OSS 对外公开域名/CDN；留空使用 Endpoint") @Size(max = 255) String ossPublicEndpoint,
        @Schema(description = "OSS 桶名称") @Size(max = 64) String ossBucket,
        @Schema(description = "OSS AccessKeyId；留空不修改") @Size(max = 255) String ossAccessKeyId,
        @Schema(description = "OSS AccessKeySecret；留空不修改") @Size(max = 255) String ossAccessKeySecret) {
}
