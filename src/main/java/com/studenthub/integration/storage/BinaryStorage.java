package com.studenthub.integration.storage;

import java.io.InputStream;

/**
 * 二进制对象存储抽象：屏蔽本地磁盘与 MinIO 等实现差异，业务模块只依赖本接口。
 */
public interface BinaryStorage {

    void put(String objectKey, byte[] content, String contentType);

    InputStream get(String objectKey);
}
