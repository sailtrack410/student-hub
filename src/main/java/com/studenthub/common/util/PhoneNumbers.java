package com.studenthub.common.util;

import java.util.regex.Pattern;

/**
 * 手机号格式校验收敛：原 UpdatePhoneRequest 注解与 IdentityAuthServiceImpl 各写一份正则。
 * PATTERN 常量为编译期常量，可直接用于 @Pattern(regexp = ...)。
 */
public final class PhoneNumbers {

    public static final String PATTERN = "1[3-9]\\d{9}";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    private PhoneNumbers() {
    }

    public static boolean isValid(String phone) {
        return phone != null && COMPILED.matcher(phone).matches();
    }
}
