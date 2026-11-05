package com.studenthub.modules.community.service.share;

import java.security.SecureRandom;

/**
 * 帖子分享短链码生成（D-Share）：
 * 8 位、字符集去掉易混淆的 0O1IiL，组合空间 54^8 ≈ 72 亿（可用 20 亿+），
 * SecureRandom 生成，唯一索引兜底碰撞重试。
 */
public final class PostShareSlugGenerator {

    private static final char[] ALPHABET =
            "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PostShareSlugGenerator() {
    }

    public static String next() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
