package com.studenthub.modules.appversion;

import com.studenthub.exception.BusinessException;
import com.studenthub.integration.storage.BinaryStorage;
import com.studenthub.modules.appversion.controller.dto.AdminAppVersionRequest;
import com.studenthub.modules.appversion.entity.AppVersionEntity;
import com.studenthub.modules.appversion.mapper.AppVersionMapper;
import com.studenthub.modules.appversion.service.AppVersionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppVersionReleaseConsistencyTest {
    @Autowired AppVersionService service;
    @MockitoSpyBean AppVersionMapper mapper;
    @Autowired BinaryStorage storage;
    @Autowired MockMvc mvc;

    @Test
    void immutableObjectsPreservePriorBytesAcrossReuploadVersionAndChannel() throws Exception {
        String channel = channel();
        long id = draft(channel, 1, null);
        byte[] oldBytes = apk("old"), newBytes = apk("new");
        upload(id, oldBytes);
        String oldKey = mapper.selectById(id).getPackageKey();
        upload(id, newBytes);
        String newKey = mapper.selectById(id).getPackageKey();
        assertNotEquals(oldKey, newKey);
        assertTrue(newKey.contains("/" + channel + "/" + id + "/"));
        try (var stream = storage.get(oldKey)) { assertArrayEquals(oldBytes, stream.readAllBytes()); }
        try (var stream = storage.get(newKey)) { assertArrayEquals(newBytes, stream.readAllBytes()); }
        long anotherVersion = draft(channel, 2, null), anotherChannel = draft(channel(), 1, null);
        upload(anotherVersion, newBytes); upload(anotherChannel, newBytes);
        assertNotEquals(newKey, mapper.selectById(anotherVersion).getPackageKey());
        assertNotEquals(newKey, mapper.selectById(anotherChannel).getPackageKey());
    }

    @Test
    void incorrectDeclaredLengthLeavesExistingPackageUnchanged() throws Exception {
        long id = draft(channel(), 1, null);
        byte[] bytes = apk("valid"); upload(id, bytes);
        String key = mapper.selectById(id).getPackageKey();
        assertThrows(BusinessException.class, () -> service.uploadPackage(id, "x.apk", new ByteArrayInputStream(apk("other")), 4));
        assertThrows(BusinessException.class, () -> service.uploadPackage(id, "x.apk", new ByteArrayInputStream(bytes), bytes.length + 1));
        assertEquals(key, mapper.selectById(id).getPackageKey());
        try (var stream = storage.get(key)) { assertArrayEquals(bytes, stream.readAllBytes()); }
    }

    @Test
    void stableDownloadTracksCurrentReleaseAndNoRelease() throws Exception {
        String channel = channel();
        mvc.perform(get("/api/public/app-version/latest/package").param("channel", channel)).andExpect(status().isNotFound());
        long first = draft(channel, 1, null); byte[] bytes = apk("first"); upload(first, bytes); service.publish(first);
        mvc.perform(get("/api/public/app-version/latest/package").param("channel", channel))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes))
                .andExpect(header().string("X-App-Version-Id", String.valueOf(first)))
                .andExpect(header().string("Cache-Control", "no-store"));
        long second = draft(channel, 2, "https://example.com/new.apk"); service.publish(second);
        mvc.perform(get("/api/public/app-version/latest/package").param("channel", channel))
                .andExpect(status().isFound()).andExpect(header().string("Location", "https://example.com/new.apk"));
        service.offline(second);
        mvc.perform(get("/api/public/app-version/latest/package").param("channel", channel)).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/app-version/latest").param("platform", "ANDROID").param("channel", channel))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.latest").isEmpty());
    }

    @Test
    void simultaneousChannelPublishesLeaveExactlyOnePublishedVersion() throws Exception {
        String channel = channel();
        long a = draft(channel, 1, "https://example.com/a.apk"), b = draft(channel, 2, "https://example.com/b.apk");
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var left = pool.submit(() -> { start.await(); return service.publish(a); });
            var right = pool.submit(() -> { start.await(); return service.publish(b); });
            start.countDown(); left.get(20, TimeUnit.SECONDS); right.get(20, TimeUnit.SECONDS);
            long active = service.list("ANDROID", "PUBLISHED").stream().filter(v -> channel.equals(v.channel())).count();
            assertEquals(1, active);
            assertTrue(("PUBLISHED".equals(mapper.selectById(a).getStatus()) && "OFFLINE".equals(mapper.selectById(b).getStatus()))
                    || ("OFFLINE".equals(mapper.selectById(a).getStatus()) && "PUBLISHED".equals(mapper.selectById(b).getStatus())));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void failedTargetWriteRollsBackOldVersionOffline() {
        String channel = channel();
        long old = draft(channel, 1, "https://example.com/a.apk"), next = draft(channel, 2, "https://example.com/b.apk");
        service.publish(old);
        doAnswer(invocation -> {
            AppVersionEntity entity = invocation.getArgument(0);
            if (entity.getId().equals(next)) return 0;
            return invocation.callRealMethod();
        }).when(mapper).updateById(any(AppVersionEntity.class));
        try { assertThrows(BusinessException.class, () -> service.publish(next)); }
        finally { reset(mapper); }
        assertEquals("PUBLISHED", mapper.selectById(old).getStatus());
        assertEquals("DRAFT", mapper.selectById(next).getStatus());
    }

    private long draft(String channel, int code, String url) {
        return service.create(new AdminAppVersionRequest("ANDROID", channel, code, "1." + code, 0, false, "notes", url)).id();
    }
    private void upload(long id, byte[] bytes) { service.uploadPackage(id, "app.apk", new ByteArrayInputStream(bytes), bytes.length); }
    private static String channel() { return "T_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(); }
    private static byte[] apk(String text) { return ("PK\u0003\u0004" + text).getBytes(StandardCharsets.UTF_8); }
}
