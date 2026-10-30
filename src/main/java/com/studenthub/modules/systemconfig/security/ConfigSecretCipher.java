package com.studenthub.modules.systemconfig.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 管理端配置中心敏感值加密（AES-256-GCM）。
 *
 * <p>密文格式：{@code enc:v1:<base64(iv + ciphertext + tag)>}。主密钥只从
 * {@code CONFIG_SECRET_KEY} 环境变量读取，使用 SHA-256 派生 AES-256 Key；
 * AAD 绑定配置键，防止把某个配置的密文复制到另一个配置项后仍能解密。</p>
 *
 * <p>主密钥为空时加密能力不可用：管理端只能看到环境变量来源的密钥是否已配置，
 * 不能保存新的密钥。这样不会因为缺少主密钥而阻断服务启动，但也不会把明文写库。</p>
 */
@Slf4j
@Component
public class ConfigSecretCipher {

    static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_KEY_LENGTH = 16;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public ConfigSecretCipher(@Value("${studenthub.config.secret-key:}") String masterKey) {
        if (!StringUtils.hasText(masterKey)) {
            this.key = null;
            log.warn("CONFIG_SECRET_KEY 未配置：管理端不能保存密钥类配置，密钥类配置仅使用环境变量");
            return;
        }
        if (masterKey.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException("CONFIG_SECRET_KEY 太短，至少需要 " + MIN_KEY_LENGTH + " 个字符");
        }
        try {
            this.key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("CONFIG_SECRET_KEY 初始化失败", exception);
        }
    }

    public boolean available() {
        return key != null;
    }

    public String encrypt(String configKey, String plaintext) {
        if (!available()) {
            throw new IllegalStateException("CONFIG_SECRET_KEY 未配置，不能保存密钥类配置");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(configKey));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception exception) {
            throw new IllegalStateException("配置密钥加密失败", exception);
        }
    }

    public String decrypt(String configKey, String stored) {
        if (!available()) {
            throw new IllegalStateException("CONFIG_SECRET_KEY 未配置，不能读取密钥类配置");
        }
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("密钥类配置不是预期的加密格式");
        }
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (data.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文长度不合法");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_LENGTH));
            cipher.updateAAD(aad(configKey));
            byte[] plain = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            log.error("配置密钥解密失败, key={}", configKey);
            throw new IllegalStateException("配置密钥解密失败", exception);
        }
    }

    public boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    private byte[] aad(String configKey) {
        return ("sys_config:" + configKey).getBytes(StandardCharsets.UTF_8);
    }
}
