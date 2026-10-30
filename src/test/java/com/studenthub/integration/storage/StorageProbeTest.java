package com.studenthub.integration.storage;

import com.studenthub.modules.file.config.FileProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageProbeTest {

    @Test
    void ossWithoutCredentialsReportsMissingInsteadOfLeakingSecrets() {
        FileProperties properties = new FileProperties();
        properties.setStorage("oss");
        OssBinaryStorage storage = new OssBinaryStorage(properties);

        StorageProbeResult result = storage.probe();

        assertFalse(result.reachable());
        assertTrue(result.detail().contains("未配置"));
        assertNull(storage.presign("a/b.png", 60), "配置不完整时签名应回退，不应抛异常");
    }

    @Test
    void ossPresignWorksOnJdk17WithoutNetwork() {
        FileProperties properties = new FileProperties();
        properties.setStorage("oss");
        properties.setOssEndpoint("https://oss-cn-chengdu.aliyuncs.com");
        properties.setOssBucket("smartcampus-test");
        properties.setOssAccessKeyId("dummy-access-key-id");
        properties.setOssAccessKeySecret("dummy-access-key-secret");
        OssBinaryStorage storage = new OssBinaryStorage(properties);

        String url = storage.presign("files/2026/10/demo.png", 60);

        assertNotNull(url, "OSS 客户端应能在 JDK 17 下生成签名 URL");
        assertTrue(url.contains("smartcampus-test"));
        assertTrue(url.contains("Expires") || url.contains("OSSAccessKeyId"));
    }

    @Test
    void routingCanProbeSpecificStorageWithoutChangingActiveStorage() {
        FileProperties properties = new FileProperties();
        properties.setStorage("local");
        properties.setStorageDir("target/test-storage-probe");
        RoutingBinaryStorage routing = new RoutingBinaryStorage(
                new LocalBinaryStorage(properties),
                new MinioBinaryStorage(properties),
                new OssBinaryStorage(properties),
                properties);

        StorageProbeResult local = routing.probe("local");
        StorageProbeResult oss = routing.probe("oss");

        assertEquals("local", local.storage());
        assertTrue(local.reachable());
        assertEquals("oss", oss.storage());
        assertFalse(oss.reachable());
        assertEquals("local", routing.probe().storage(), "指定目标探测不能改变当前活跃存储");
    }

    @Test
    void routingUsesLocalStorageAndChecksWritableDirectory() {
        FileProperties properties = new FileProperties();
        properties.setStorage("local");
        properties.setStorageDir("target/test-storage-probe");
        RoutingBinaryStorage routing = new RoutingBinaryStorage(
                new LocalBinaryStorage(properties),
                new MinioBinaryStorage(properties),
                new OssBinaryStorage(properties),
                properties);

        StorageProbeResult result = routing.probe();

        assertTrue(result.reachable());
        assertTrue(result.detail().contains("可写"));
    }
}
