package com.studenthub.modules.push.provider;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推送通道注册表测试：未配置通道必须返回 NOT_CONFIGURED，异常通道必须返回 FAILED。
 *
 * <p>这两条是「不伪造到达」的底线保证：
 * 未配置通道既不能算成功也不能算失败；通道异常不能被静默吞掉。</p>
 */
class PushProviderRegistryTest {

    @Test
    void shouldReturnNotConfiguredWhenProviderNotConfigured() {
        PushProvider unconfigured = new StubProvider("FCM", false, null, PushDeliveryResult.sent("should-not-use"));
        PushProviderRegistry registry = new PushProviderRegistry(List.of(unconfigured));

        PushDeliveryResult result = registry.send("fcm", message());

        assertEquals(PushDeliveryStatus.NOT_CONFIGURED, result.status());
        assertEquals("PUSH_NOT_CONFIGURED", result.errorCode());
    }

    @Test
    void shouldReturnNotConfiguredForUnknownChannel() {
        PushProviderRegistry registry = new PushProviderRegistry(List.of());

        PushDeliveryResult result = registry.send("APNS", message());

        assertEquals(PushDeliveryStatus.NOT_CONFIGURED, result.status());
        assertTrue(result.message().contains("APNS"));
    }

    @Test
    void shouldReturnFailedWhenProviderThrows() {
        PushProvider broken = new StubProvider("WS", true, null, null);
        PushProviderRegistry registry = new PushProviderRegistry(List.of(broken));

        PushDeliveryResult result = registry.send("WS", message());

        assertEquals(PushDeliveryStatus.FAILED, result.status());
        assertEquals("PUSH_PROVIDER_ERROR", result.errorCode());
    }

    @Test
    void shouldExposeChannelCapabilities() {
        PushProvider log = new StubProvider("LOG", true, null, PushDeliveryResult.sent("ok"));
        PushProvider fcm = new StubProvider("FCM", false, "fcm 未启用", PushDeliveryResult.sent("no"));
        PushProviderRegistry registry = new PushProviderRegistry(List.of(log, fcm));

        List<PushProviderRegistry.ChannelStatus> statuses = registry.statuses();

        assertEquals(2, statuses.size());
        assertTrue(registry.resolve("LOG").configured());
        assertFalse(registry.resolve("FCM").configured());
        assertEquals("fcm 未启用", registry.resolve("FCM").unconfiguredReason());
    }

    @Test
    void shouldParseAndValidateChannels() {
        assertEquals(List.of("LOG", "WS"), PushChannels.parseAndValidate(" log , ws "));
        assertEquals(List.of(), PushChannels.parse(null));
        assertTrue(PushChannels.parse("LOG,WS").contains("WS"));
        try {
            PushChannels.parseAndValidate("MAIL");
            throw new AssertionError("未知渠道应当抛出 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("MAIL"));
        }
    }

    private PushMessage message() {
        return new PushMessage(1L, 2L, "标题", "正文", Map.of());
    }

    /** 测试替身：可控制 configured 与返回值（result 为 null 时模拟通道异常）。 */
    private record StubProvider(String channel, boolean configured, String reason,
                                PushDeliveryResult result) implements PushProvider {

        @Override
        public boolean configured() {
            return configured;
        }

        @Override
        public String unconfiguredReason() {
            return reason;
        }

        @Override
        public PushDeliveryResult send(PushMessage message) {
            if (result == null) {
                throw new IllegalStateException("模拟通道异常");
            }
            return result;
        }
    }
}
