package com.studenthub.integration.storage;

import com.studenthub.modules.file.config.FileProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘存储适配器：默认实现（开发与测试使用），文件平铺在 {storageDir} 下，键即文件名。
 */
@Component
@ConditionalOnProperty(prefix = "studenthub.file", name = "storage", havingValue = "local", matchIfMissing = true)
public class LocalBinaryStorage implements BinaryStorage {

    private final FileProperties properties;

    public LocalBinaryStorage(FileProperties properties) {
        this.properties = properties;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        try {
            Path target = Path.of(properties.getStorageDir(), objectKey);
            Files.createDirectories(target.getParent());
            Files.copy(new java.io.ByteArrayInputStream(content), target,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("本地文件写入失败", exception);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return Files.newInputStream(Path.of(properties.getStorageDir(), objectKey));
        } catch (IOException exception) {
            throw new IllegalStateException("本地文件读取失败: " + objectKey, exception);
        }
    }
}
