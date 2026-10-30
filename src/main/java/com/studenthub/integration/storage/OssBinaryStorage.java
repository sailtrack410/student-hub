package com.studenthub.integration.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.studenthub.modules.file.config.FileProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;
import java.util.Objects;

/**
 * 阿里云 OSS 对象存储适配器：FileProperties.storage=oss 时由路由使用。
 *
 * <p>客户端按当前配置懒创建，管理端修改 Endpoint/桶/AK/SK 后无需重启。
 * AccessKey 只保存在后端配置中心（加密存储），不会出现在接口、日志或签名 URL 中。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssBinaryStorage implements BinaryStorage {

    private static final int MAX_PRESIGN_SECONDS = 7 * 24 * 3600;

    private final FileProperties properties;

    private volatile Holder holder;

    private record Holder(OSS client, String bucket, String endpoint, String friendlyName, String fingerprint) {
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        Holder current = holder();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        if (contentType != null && !contentType.isBlank()) {
            metadata.setContentType(contentType);
        }
        try {
            current.client().putObject(current.bucket(), objectKey,
                    new ByteArrayInputStream(content), metadata);
        } catch (Exception exception) {
            log.error("OSS 写入失败, endpoint={}, key={}", current.friendlyName(), objectKey, exception);
            throw new IllegalStateException("OSS 写入失败", exception);
        }
    }

    @Override
    public void putStream(String objectKey, InputStream content, long size, String contentType) {
        Holder current = holder();
        ObjectMetadata metadata = new ObjectMetadata();
        if (size >= 0) {
            metadata.setContentLength(size);
        }
        if (contentType != null && !contentType.isBlank()) {
            metadata.setContentType(contentType);
        }
        try {
            current.client().putObject(current.bucket(), objectKey, content, metadata);
        } catch (Exception exception) {
            log.error("OSS 流式写入失败, endpoint={}, key={}", current.friendlyName(), objectKey, exception);
            throw new IllegalStateException("OSS 流式写入失败", exception);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        Holder current = holder();
        try {
            OSSObject object = current.client().getObject(current.bucket(), objectKey);
            return object.getObjectContent();
        } catch (Exception exception) {
            log.error("OSS 读取失败, endpoint={}, key={}", current.friendlyName(), objectKey, exception);
            throw new IllegalStateException("OSS 读取失败", exception);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        Holder current = holder();
        try {
            return current.client().doesObjectExist(current.bucket(), objectKey);
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
            log.warn("{} 签名地址生成失败：存储配置不完整, key={}", "OSS", objectKey);
            return null;
        }
        OSS client = current.client();
        String publicEndpoint = properties.getOssPublicEndpoint();
        boolean useCname = publicEndpoint != null && !publicEndpoint.isBlank();
        if (useCname) {
            // APK 公网下载必须走 Bucket 绑定的 CNAME；必须让 SDK 用同一主机完成签名，
            // 不能先按默认 OSS 域名签名再替换 Host，否则 SignatureDoesNotMatch。
            ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
            configuration.setSupportCname(true);
            client = new OSSClientBuilder().build(normalizeEndpoint(publicEndpoint),
                    properties.getOssAccessKeyId(), properties.getOssAccessKeySecret(), configuration);
        }
        try {
            int boundedExpiry = Math.max(1, Math.min(expirySeconds, MAX_PRESIGN_SECONDS));
            Date expiration = new Date(System.currentTimeMillis() + boundedExpiry * 1000L);
            return client.generatePresignedUrl(current.bucket(), objectKey, expiration).toString();
        } catch (Exception exception) {
            log.warn("OSS 签名地址生成失败, endpoint={}, key={}", current.friendlyName(), objectKey, exception);
            return null;
        } finally {
            if (useCname) {
                client.shutdown();
            }
        }
    }

    public StorageProbeResult probe() {
        long start = System.currentTimeMillis();
        try {
            Holder current = holder();
            boolean exists = current.client().doesBucketExist(current.bucket());
            long latency = System.currentTimeMillis() - start;
            return new StorageProbeResult(exists, latency, "oss",
                    exists ? "OSS 桶可用：" + current.friendlyName()
                            : "OSS 可达，但桶不存在：" + current.bucket());
        } catch (Exception exception) {
            return new StorageProbeResult(false, System.currentTimeMillis() - start, "oss",
                    "OSS 不可用：" + safeMessage(exception));
        }
    }

    @PreDestroy
    public void close() {
        Holder current = holder;
        if (current != null) {
            try {
                current.client().shutdown();
            } catch (Exception exception) {
                log.debug("关闭 OSS 客户端失败: {}", exception.getMessage());
            }
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
            String endpoint = normalizeEndpoint(required(properties.getOssEndpoint(), "OSS Endpoint"));
            String accessKeyId = required(properties.getOssAccessKeyId(), "OSS AccessKeyId");
            String accessKeySecret = required(properties.getOssAccessKeySecret(), "OSS AccessKeySecret");
            String bucket = required(properties.getOssBucket(), "OSS 桶");
            OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            Holder previous = holder;
            if (previous != null && previous.client() != client) {
                try {
                    previous.client().shutdown();
                } catch (Exception exception) {
                    log.debug("关闭旧 OSS 客户端失败: {}", exception.getMessage());
                }
            }
            Holder next = new Holder(client, bucket, endpoint, endpoint + "/" + bucket, fingerprint);
            holder = next;
            log.info("阿里云 OSS 已按最新配置初始化, endpoint={}/{}", endpoint, bucket);
            return next;
        }
    }

    private String fingerprint() {
        return String.join("|",
                Objects.toString(properties.getOssEndpoint(), ""),
                Objects.toString(properties.getOssAccessKeyId(), ""),
                Objects.toString(properties.getOssAccessKeySecret(), ""),
                Objects.toString(properties.getOssBucket(), ""));
    }

    private String normalizeEndpoint(String endpoint) {
        String value = endpoint.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return "https://" + value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
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
        for (String secret : new String[]{properties.getOssAccessKeyId(), properties.getOssAccessKeySecret()}) {
            if (secret != null && !secret.isBlank()) {
                message = message.replace(secret, "******");
            }
        }
        return message.length() > 180 ? message.substring(0, 180) + "…" : message;
    }
}
