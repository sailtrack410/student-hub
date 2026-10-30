package com.studenthub.modules.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studenthub.file")
public class FileProperties {

    /** 存储实现：local / minio / oss（默认本地，开发测试用） */
    private String storage = "local";

    /** 本地存储目录（storage=local 时使用） */
    private String storageDir = "./data/files";

    /** 短时签名访问的 HMAC 密钥；多实例部署必须通过环境变量统一 */
    private String signSecret = "dev-sign-secret-change-me";

    /** 单文件大小上限（MB） */
    private int maxFileSizeMb = 5;

    /** 签名访问链接有效期（分钟） */
    private int viewTtlMinutes = 30;

    /** MinIO 访问端点，如 http://host:9000 */
    private String minioEndpoint = "http://localhost:9000";

    /** MinIO 对客户端公开的 HTTPS 端点，如 https://student.sailtrack.cn；留空则使用访问端点 */
    private String minioPublicEndpoint;

    private String minioAccessKey;

    private String minioSecretKey;

    /** MinIO 桶名称 */
    private String minioBucket = "student-hub";

    /** 阿里云 OSS Endpoint，如 https://oss-cn-chengdu.aliyuncs.com */
    private String ossEndpoint;
    /** OSS 对客户端公开的访问域名/CDN；留空则使用 endpoint */
    private String ossPublicEndpoint;
    /** OSS 桶名称 */
    private String ossBucket;
    /** OSS AccessKey ID（仅后端使用，管理端加密存储） */
    private String ossAccessKeyId;
    /** OSS AccessKey Secret（仅后端使用，管理端加密存储） */
    private String ossAccessKeySecret;

    /** 当前是否为对象存储实现。 */
    public boolean objectStorage() {
        String value = storage == null ? "local" : storage.trim().toLowerCase();
        return "minio".equals(value) || "oss".equals(value);
    }

    public String normalizedStorage() {
        return storage == null || storage.isBlank() ? "local" : storage.trim().toLowerCase();
    }
}
