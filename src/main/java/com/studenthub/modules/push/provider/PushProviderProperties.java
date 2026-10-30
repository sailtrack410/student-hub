package com.studenthub.modules.push.provider;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部推送通道配置（默认全关，缺失即为未配置）。
 *
 * <p>配置键（application.yaml / 环境变量，由 Lead 统一维护，本模块不做修改）：
 * <pre>
 * studenthub:
 *   push:
 *     fcm:     { enabled: false, credentials: "", endpoint: "" }
 *     apns:    { enabled: false, credentials: "", endpoint: "" }
 *     webpush: { enabled: false, credentials: "", endpoint: "" }
 * </pre>
 * 未配置时对应通道返回 NOT_CONFIGURED，绝不伪造到达。
 */
@Data
@Component
@ConfigurationProperties(prefix = "studenthub.push")
public class PushProviderProperties {

    private External fcm = new External();
    private External apns = new External();
    private External webPush = new External();

    @Data
    public static class External {
        /** 是否启用该通道；默认 false。 */
        private boolean enabled = false;
        /** 凭证（服务账号 JSON / APNs 私钥 / VAPID 私钥）；禁止写入代码或日志。 */
        private String credentials = "";
        /** 通道端点覆盖（可选）。 */
        private String endpoint = "";
    }
}
