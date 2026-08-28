package com.studenthub.modules.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studenthub.file")
public class FileProperties {

    /** 存储实现：minio 或 local（默认本地，开发测试用） */
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

    private String minioAccessKey;

    private String minioSecretKey;

    /** MinIO 桶名称 */
    private String minioBucket = "student-hub";
}
