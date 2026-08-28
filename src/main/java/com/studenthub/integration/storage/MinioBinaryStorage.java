package com.studenthub.integration.storage;

import com.studenthub.modules.file.config.FileProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
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
    private final String bucket;
    private final String friendlyName;

    public MinioBinaryStorage(FileProperties properties) {
        this.minioClient = MinioClient.builder()
                .endpoint(properties.getMinioEndpoint())
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
}
