package com.studenthub.integration.storage;

import java.io.InputStream;

/**
 * 二进制对象存储抽象：屏蔽本地磁盘与 MinIO 等实现差异，业务模块只依赖本接口。
 */
public interface BinaryStorage {

    void put(String objectKey, byte[] content, String contentType);

    InputStream get(String objectKey);

    default boolean exists(String objectKey) {
        return false;
    }

    /**
     * 生成可由客户端直接访问的短时签名地址。非对象存储实现返回 null，
     * 由上层回退到本地 API 签名地址。
     */
    default String presign(String objectKey, int expirySeconds) {
        return null;
    }
}
