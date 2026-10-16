package com.studenthub.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256-hex 摘要收敛：原先 Mock 微信/QQ 身份提供者、identity 的 SecureTokenSupport、
 * rag 的 KnowledgeBaseService 各持一份 MessageDigest("SHA-256") + hex 格式化实现，统一到这里。
 */
public final class Digests {

    private Digests() {
    }

    /** SHA-256 摘要的小写 hex（64 字符），输入按 UTF-8 编码。 */
    public static String sha256Hex(String input) {
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 摘要的小写 hex（64 字符），供二进制内容（知识库文档等）直接使用。 */
    public static String sha256Hex(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
