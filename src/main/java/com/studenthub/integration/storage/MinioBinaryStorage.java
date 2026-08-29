package com.studenthub.integration.storage;

import com.studenthub.modules.file.config.FileProperties;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * MinIO 对象存储适配器：studenthub.file.storage=minio 时启用。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "studenthub.file", name = "storage", havingValue = "minio")
public class MinioBinaryStorage implements BinaryStorage {

    private final MinioClient minioClient;
    private final MinioClient publicClient;
    private final String bucket;
    private final String friendlyName;

    public MinioBinaryStorage(FileProperties properties) {
        this.minioClient = MinioClient.builder()
                .endpoint(properties.getMinioEndpoint())
                .credentials(properties.getMinioAccessKey(), properties.getMinioSecretKey())
                .build();
        String publicEndpoint = properties.getMinioPublicEndpoint();
        this.publicClient = publicEndpoint == null || publicEndpoint.isBlank()
                ? this.minioClient
                : MinioClient.builder()
                        .endpoint(publicEndpoint)
                        .credentials(properties.getMinioAccessKey(), properties.getMinioSecretKey())
                        .build();
        this.bucket = properties.getMinioBucket();
        this.friendlyName = properties.getMinioEndpoint() + "/" + bucket;
        log.info("MinIO 对象存储已启用, endpoint={}/{}", properties.getMinioEndpoint(), bucket);
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .contentType(contentType)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .build());
        } catch (Exception exception) {
            log.error("MinIO 写入失败, endpoint={}, key={}", friendlyName, objectKey, exception);
            throw new IllegalStateException("MinIO 写入失败", exception);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            log.error("MinIO 读取失败, endpoint={}, key={}", friendlyName, objectKey, exception);
            throw new IllegalStateException("MinIO 读取失败", exception);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public String presign(String objectKey, int expirySeconds) {
        try {
            int boundedExpiry = Math.max(1, Math.min(expirySeconds, 7 * 24 * 3600));
            return publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(boundedExpiry)
                    .build());
        } catch (Exception exception) {
            log.warn("MinIO 签名地址生成失败, endpoint={}, key={}", friendlyName, objectKey, exception);
            return null;
        }
    }
}
