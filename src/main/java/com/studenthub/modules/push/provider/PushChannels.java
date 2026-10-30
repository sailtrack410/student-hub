package com.studenthub.modules.push.provider;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 渠道编码常量与解析工具。 */
public final class PushChannels {

    public static final String LOG = "LOG";
    public static final String WS = "WS";
    public static final String FCM = "FCM";
    public static final String APNS = "APNS";
    public static final String WEBPUSH = "WEBPUSH";

    public static final Set<String> ALL = Set.of(LOG, WS, FCM, APNS, WEBPUSH);

    private PushChannels() {
    }

    /** 解析逗号分隔渠道串：去空、转大写、去重、保序；非法渠道由调用方校验。 */
    public static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String value = part.trim().toUpperCase(Locale.ROOT);
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    /** 规范化并校验：返回去重后的渠道串，遇到未知渠道抛 IllegalArgumentException（由 Service 转业务异常）。 */
    public static List<String> parseAndValidate(String raw) {
        List<String> channels = parse(raw);
        for (String channel : channels) {
            if (!ALL.contains(channel)) {
                throw new IllegalArgumentException("不支持的推送渠道：" + channel);
            }
        }
        return channels;
    }

    /** 仅保留当前进程可真实投递的渠道（用于测试发送时如实告知生效范围）。 */
    public static List<String> configuredOnly(List<String> channels, PushProviderRegistry registry) {
        return channels.stream().filter(channel -> {
            PushProvider provider = registry.resolve(channel);
            return provider != null && provider.configured();
        }).toList();
    }
}
