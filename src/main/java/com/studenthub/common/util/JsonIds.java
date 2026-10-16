package com.studenthub.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片文件 ID 列表的 JSON 序列化/反序列化收敛工具。
 * 原先散落在 workorder/community/servicerequest/aichat 各服务里的 parseIds/writeIds 私有副本统一到这里。
 *
 * 异常策略（对齐原实现中的吞法）：
 * - parse：空白或解析失败一律静默返回空列表（历史脏数据可容忍，读取路径不抛异常）；
 *   parseOrNull 变体供「可空字段」场景使用（如 AI 消息 images），失败返回 null 而非空列表。
 * - write：序列化失败直接抛 IllegalStateException（写入路径失败必须中断事务）。
 *
 * 返回可变列表：parse 返回 ArrayList，调用方（工单完工照片追加）可直接 add。
 */
public final class JsonIds {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Long>> TYPE = new TypeReference<>() { };

    private JsonIds() {
    }

    /** 空白或解析失败返回空可变列表 */
    public static List<Long> parse(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(MAPPER.readValue(json, TYPE));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    /** 空白或解析失败返回 null，供「可空字段」语义的响应 DTO 使用 */
    public static List<Long> parseOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 序列化失败抛 IllegalStateException；空列表由调用方决定写 "[]" 还是 null */
    public static String write(List<Long> ids) {
        try {
            return MAPPER.writeValueAsString(ids);
        } catch (Exception exception) {
            throw new IllegalStateException("图片文件 ID 列表序列化失败", exception);
        }
    }
}
