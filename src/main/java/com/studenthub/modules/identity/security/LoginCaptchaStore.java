package com.studenthub.modules.identity.security;

import java.time.Duration;

/** 一次性验证码存储；consume 原子读取并删除，错误答案同样消耗挑战。 */
public interface LoginCaptchaStore {
    void put(String id, String answer, Duration ttl);
    String consume(String id);
}
