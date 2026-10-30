package com.studenthub.integration.storage;

import com.studenthub.modules.file.config.FileProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;

/**
 * MinIO 对象存储适配器：FileProperties.storage=minio 时由路由使用。
 *
 * <p>客户端按当前配置懒创建；管理端修改 endpoint/AK/SK/桶后无需重启，
 * 下次操作检测到配置指纹变化会自动重建。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioBinaryStorage implements BinaryStorage {

    private final FileProperties properties;

    private volatile Holder holder;

    private record Holder(MinioClient client, MinioClient publicClient, String bucket,
                          String friendlyName, String fingerprint) {
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        Holder current = holder();
        try {
            current.client().putObject(PutObjectArgs.builder()
                    .bucket(current.bucket())
                    .object(objectKey)
                    .contentType(contentType)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .build());
        } catch (Exception exception) {
            log.error("MinIO 写入失败, endpoint={}, key={}", current.friendlyName(), objectKey, exception);
            throw new IllegalStateException("MinIO 写入失败", exception);
        }
    }

    @Override
    public void putStream(String objectKey, InputStream content, long size, String contentType) {
        Holder current = holder();
        try {
            current.client().putObject(PutObjectArgs.builder()
                    .bucket(current.bucket())
                    .object(objectKey)
                    .contentType(contentType)
                    .stream(content, size, -1)
                    .build());
        } catch (Exception exception) {
            log.error("MinIO 流式写入失败, endpoint={}, key={}", current.friendlyName(), objectKey, exception);
            throw new IllegalStateException("MinIO 流式写入失败", exception);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        Holder current = holder();
        try {
            return current.client().getObject(GetObjectArgs.builder()
                    .bucket(current.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            log.error("MinIO 读取失败, endpoint={}, key={}", current.friendlyName(), objectKey, exception);
            throw new IllegalStateException("MinIO 读取失败", exception);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        Holder current = holder();
        try {
            current.client().statObject(StatObjectArgs.builder()
                    .bucket(current.bucket()).object(objectKey).build());
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public String presign(String objectKey, int expirySeconds) {
        Holder current;
        try {
            current = holder();
        } catch (Exception exception) {
            log.warn("{} 签名地址生成失败：存储配置不完整, key={}", "MinIO", objectKey);
            return null;
        }
        try {
            int boundedExpiry = Math.max(1, Math.min(expirySeconds, 7 * 24 * 3600));
            return current.publicClient().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(current.bucket())
                    .object(objectKey)
                    .expiry(boundedExpiry)
                    .build());
        } catch (Exception exception) {
            log.warn("MinIO 签名地址生成失败, endpoint={}, key={}", current.friendlyName(), objectKey, exception);
            return null;
        }
    }

    public StorageProbeResult probe() {
        long start = System.currentTimeMillis();
        try {
            Holder current = holder();
            boolean exists = current.client().bucketExists(BucketExistsArgs.builder()
                    .bucket(current.bucket()).build());
            long latency = System.currentTimeMillis() - start;
            return new StorageProbeResult(exists, latency, "minio",
                    exists ? "MinIO 桶可用：" + current.friendlyName()
                            : "MinIO 可达，但桶不存在：" + current.bucket());
        } catch (Exception exception) {
            return new StorageProbeResult(false, System.currentTimeMillis() - start, "minio",
                    "MinIO 不可用：" + safeMessage(exception));
        }
    }

    private Holder holder() {
        String fingerprint = fingerprint();
        Holder current = holder;
        if (current != null && current.fingerprint().equals(fingerprint)) {
            return current;
        }
        synchronized (this) {
            fingerprint = fingerprint();
            current = holder;
            if (current != null && current.fingerprint().equals(fingerprint)) {
                return current;
            }
            String endpoint = required(properties.getMinioEndpoint(), "MinIO Endpoint");
            String accessKey = required(properties.getMinioAccessKey(), "MinIO AccessKey");
            String secretKey = required(properties.getMinioSecretKey(), "MinIO SecretKey");
            String bucket = required(properties.getMinioBucket(), "MinIO 桶");
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            String publicEndpoint = properties.getMinioPublicEndpoint();
            MinioClient publicClient = publicEndpoint == null || publicEndpoint.isBlank()
                    ? client
                    : MinioClient.builder().endpoint(publicEndpoint).credentials(accessKey, secretKey).build();
            Holder next = new Holder(client, publicClient, bucket, endpoint + "/" + bucket, fingerprint);
            holder = next;
            log.info("MinIO 对象存储已按最新配置初始化, endpoint={}/{}", endpoint, bucket);
            return next;
        }
    }

    private String fingerprint() {
        return String.join("|",
                Objects.toString(properties.getMinioEndpoint(), ""),
                Objects.toString(properties.getMinioPublicEndpoint(), ""),
                Objects.toString(properties.getMinioAccessKey(), ""),
                Objects.toString(properties.getMinioSecretKey(), ""),
                Objects.toString(properties.getMinioBucket(), ""));
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 未配置");
        }
        return value.trim();
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName()
                : exception.getMessage().replaceAll("[\\r\\n]+", " ").trim();
        for (String secret : new String[]{properties.getMinioAccessKey(), properties.getMinioSecretKey()}) {
            if (secret != null && !secret.isBlank()) {
                message = message.replace(secret, "******");
            }
        }
        return message.length() > 180 ? message.substring(0, 180) + "…" : message;
    }
}
