package com.studenthub.modules.systemconfig.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSecretCipherTest {

    private static final String KEY = "test-only-config-secret-key-0123456789abcdef";

    @Test
    void encryptsAndDecryptsWithConfigKeyAsAad() {
        ConfigSecretCipher cipher = new ConfigSecretCipher(KEY);
        String encrypted = cipher.encrypt("wechat.app_secret", "super-secret-value");

        assertTrue(encrypted.startsWith(ConfigSecretCipher.PREFIX));
        assertNotEquals("super-secret-value", encrypted);
        assertEquals("super-secret-value", cipher.decrypt("wechat.app_secret", encrypted));
    }

    @Test
    void cipherTextCannotBeMovedToAnotherConfigKey() {
        ConfigSecretCipher cipher = new ConfigSecretCipher(KEY);
        String encrypted = cipher.encrypt("qq.client_secret", "qq-key");

        assertThrows(IllegalStateException.class,
                () -> cipher.decrypt("wechat.app_secret", encrypted));
    }

    @Test
    void rejectsTamperedCipherText() {
        ConfigSecretCipher cipher = new ConfigSecretCipher(KEY);
        String encrypted = cipher.encrypt("mail.password", "smtp-password");
        char last = encrypted.charAt(encrypted.length() - 1);
        String tampered = encrypted.substring(0, encrypted.length() - 1)
                + (last == 'A' ? 'B' : 'A');

        assertThrows(IllegalStateException.class, () -> cipher.decrypt("mail.password", tampered));
    }

    @Test
    void missingMasterKeyDisablesEncryptionInsteadOfStoringPlaintext() {
        ConfigSecretCipher cipher = new ConfigSecretCipher("");

        assertFalse(cipher.available());
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cipher.encrypt("mail.password", "plain"));
        assertTrue(exception.getMessage().contains("CONFIG_SECRET_KEY"));
    }

    @Test
    void rejectsTooShortMasterKey() {
        assertThrows(IllegalStateException.class, () -> new ConfigSecretCipher("short"));
    }
}
