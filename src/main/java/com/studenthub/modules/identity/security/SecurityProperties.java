package com.studenthub.modules.identity.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "studenthub.security")
public class SecurityProperties {

    private String sessionStore = "memory";

    /** Web/管理端等默认客户端会话有效期（短 TTL，安全优先） */
    private Duration sessionTtl = Duration.ofHours(8);

    /** App 端（学生端）会话有效期：长期免登录，配合滑动续期 */
    private Duration appSessionTtl = Duration.ofDays(30);

    /** 滑动续期阈值：剩余有效期低于该值时刷新回完整 TTL；设为 0 关闭滑动续期 */
    private Duration sessionRenewThreshold = Duration.ofDays(7);

    /** 客户端类型判定请求头与取值（忽略大小写），默认 x-client-type: app */
    private String appClientHeader = "x-client-type";
    private String appClientValue = "app";

    private boolean passwordLoginEnabled;
    private List<String> allowedOrigins = new ArrayList<>();
}
