package com.studenthub.modules.aichat.enums;

/**
 * AI 模型类型枚举：全仓模型类型口径的唯一来源（替代散落的 TYPE_* 常量与 DTO 正则）。
 *
 * <p>{@link #runtimeSupported()} 标记可进运行时快照的类型——与
 * {@code AiConfigServiceImpl#refreshRuntime} 解析并填入
 * {@code AiRuntimeSnapshot} 的 chat/vision/asr/embedding 四个绑定槽位一一对应；
 * TTS/RERANK 目前只做模型目录管理，没有运行时消费方，故不进快照。
 */
public enum AiModelType {

    CHAT("语言对话"),
    VISION("图片识别"),
    ASR("语音转写"),
    TTS("语音合成"),
    EMBEDDING("向量模型"),
    RERANK("精排");

    /**
     * DTO {@code @Pattern} 校验用的全值正则（6 值）。
     * 注解属性要求编译期常量，无法用 values() 动态拼接，
     * 故与上方枚举值同处一处维护：增删枚举值时同步改这里（有编译期引用的两处 DTO 校验兜底）。
     */
    public static final String CODE_PATTERN = "^(?:CHAT|VISION|ASR|TTS|EMBEDDING|RERANK)$";

    private final String display;

    AiModelType(String display) {
        this.display = display;
    }

    /** 中文展示名（管理端默认模型列表等处使用） */
    public String display() {
        return display;
    }

    /** 是否可进运行时快照（有对应默认模型解析与消费方）：CHAT/VISION/ASR/EMBEDDING */
    public boolean runtimeSupported() {
        return this == CHAT || this == VISION || this == ASR || this == EMBEDDING;
    }

    /** 按类型码解析；未知或 null 返回 null（兼容 DB 历史数据） */
    public static AiModelType of(String typeCode) {
        if (typeCode == null) {
            return null;
        }
        for (AiModelType type : values()) {
            if (type.name().equals(typeCode)) {
                return type;
            }
        }
        return null;
    }
}
