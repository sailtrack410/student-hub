package com.studenthub.common.util;

/**
 * 轻量 token 估算（不引入 tokenizer 依赖）：
 * 中文按 charsPerToken（默认 1.5 字/token）估算，非中文按 4 字符/token 估算，
 * 再加固定消息开销与 10% 安全系数。用于上下文预算，不用于计费。
 */
public final class TokenEstimator {

    private static final int MESSAGE_OVERHEAD = 4;
    private static final double SAFETY_FACTOR = 1.10;

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        return estimate(text, 1.5);
    }

    public static int estimate(String text, double charsPerToken) {
        if (text == null || text.isEmpty()) {
            return MESSAGE_OVERHEAD;
        }
        double ratio = charsPerToken <= 0 ? 1.5 : charsPerToken;
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (isCjk(cp)) {
                cjk++;
            } else {
                other++;
            }
        }
        double tokens = cjk / ratio + other / 4.0;
        return (int) Math.ceil(tokens * SAFETY_FACTOR) + MESSAGE_OVERHEAD;
    }

    public static int estimateMessages(java.util.List<String> texts, double charsPerToken) {
        int total = 0;
        if (texts != null) {
            for (String text : texts) {
                total += estimate(text, charsPerToken);
            }
        }
        return total;
    }

    /** 中文（含常用扩展区）按单字计量；日文假名/韩文也按 CJK 处理。 */
    private static boolean isCjk(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
