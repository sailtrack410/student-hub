package com.studenthub.integration.storage;

import com.studenthub.modules.file.config.FileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 存储路由：按文件存储页当前的 FileProperties.storage 选择 local / MinIO / OSS。
 *
 * <p>三个后端始终注册为独立 Bean，客户端懒创建。管理端保存存储设置后热更新
 * FileProperties，本路由下一次操作立即切换到新实现，不需要重启。</p>
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class RoutingBinaryStorage implements BinaryStorage {

    private final LocalBinaryStorage localBinaryStorage;
    private final MinioBinaryStorage minioBinaryStorage;
    private final OssBinaryStorage ossBinaryStorage;
    private final FileProperties properties;

    private BinaryStorage active() {
        String storage = properties.normalizedStorage();
        return switch (storage) {
            case "local" -> localBinaryStorage;
            case "minio" -> minioBinaryStorage;
            case "oss" -> ossBinaryStorage;
            default -> throw new IllegalStateException("不支持的存储实现: " + storage);
        };
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        active().put(objectKey, content, contentType);
    }

    @Override
    public void putStream(String objectKey, InputStream content, long size, String contentType) {
        active().putStream(objectKey, content, size, contentType);
    }

    @Override
    public InputStream get(String objectKey) {
        return active().get(objectKey);
    }

    @Override
    public boolean exists(String objectKey) {
        return active().exists(objectKey);
    }

    @Override
    public String presign(String objectKey, int expirySeconds) {
        return active().presign(objectKey, expirySeconds);
    }

    /** 管理端当前存储连通性测试；不返回任何 AccessKey/Secret。 */
    public StorageProbeResult probe() {
        return probe(properties.normalizedStorage());
    }

    /**
     * 测试指定存储实现，使用当前已保存的配置，不改变正在生效的存储路由。
     * 供管理端分别测试 local / MinIO / OSS，避免“测试的到底是谁”不透明。
     */
    public StorageProbeResult probe(String targetStorage) {
        String storage = targetStorage == null || targetStorage.isBlank()
                ? properties.normalizedStorage()
                : targetStorage.trim().toLowerCase(java.util.Locale.ROOT);
        if ("local".equals(storage)) {
            long start = System.currentTimeMillis();
            try {
                Path dir = Path.of(properties.getStorageDir() == null ? "./data/files" : properties.getStorageDir());
                Files.createDirectories(dir);
                boolean writable = Files.isWritable(dir);
                return new StorageProbeResult(writable, System.currentTimeMillis() - start, "local",
                        writable ? "本地目录可写：" + dir.toAbsolutePath().normalize()
                                : "本地目录不可写：" + dir.toAbsolutePath().normalize());
            } catch (Exception exception) {
                return new StorageProbeResult(false, System.currentTimeMillis() - start, "local",
                        "本地目录不可用：" + safeMessage(exception));
            }
        }
        if ("minio".equals(storage)) {
            return minioBinaryStorage.probe();
        }
        if ("oss".equals(storage)) {
            return ossBinaryStorage.probe();
        }
        return new StorageProbeResult(false, 0, storage, "不支持的存储实现：" + storage);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName()
                : exception.getMessage().replaceAll("[\\r\\n]+", " ").trim();
        return message.length() > 180 ? message.substring(0, 180) + "…" : message;
    }
}
