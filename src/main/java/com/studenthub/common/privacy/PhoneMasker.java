package com.studenthub.common.privacy;

import org.springframework.util.StringUtils;

/**
 * 管理端列表手机号脱敏。展示最小必要信息，完整号码仍由用户本人在 App 内查看。
 */
public final class PhoneMasker {

    private PhoneMasker() {
    }

    public static String mask(String phone) {
        if (!StringUtils.hasText(phone)) {
            return phone;
        }
        String value = phone.trim();
        if (value.length() <= 4) {
            return "****";
        }
        if (value.length() <= 8) {
            return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }
}
