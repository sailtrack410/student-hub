package com.studenthub.modules.jwauth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studenthub.jw")
public class JwProperties {

    /** SSO 统一身份认证基地址 */
    private String ssoBaseUrl = "https://sso.mycc.edu.cn";

    /** 教务系统基地址 */
    private String jwBaseUrl = "https://jwgl.mycc.edu.cn";

    /** CAS service 参数 */
    private String casService = "http://jwgl.mycc.edu.cn/caslogin";

    /** SSO 登录 RSA 公钥模数（hex），来自学校统一身份前端公开常量 */
    private String rsaModulus  = "00b5eeb166e069920e80bebd1fea4829d3d1f3216f2aabe79b6c47a3c18dcee5fd22c2e7ac519cab59198ece036dcf289ea8"
            + "201e2a0b9ded307f8fb704136eaeb670286f5ad44e691005ba9ea5af04ada5367cd724b5a26fdb5120cc95b6431604bd219c"
            + "6b7d83a6f8f24b43918ea988a76f93c333aa5a20991493d4eb1117e7b1";

    private String rsaExponent = "010001";

    /** 教务会话保活时长上限（小时），到期需重新认证 */
    private int sessionTtlHours = 48;

    private int connectTimeoutSeconds = 5;

    private int readTimeoutSeconds = 15;

    /** 教务会话加密密钥（AES），多实例部署必须通过环境变量统一 */
    private String encryptKey = "dev-jw-encrypt-key-change-me";

    /** 单用户每分钟认证尝试上限 */
    private int authRateLimitPerMinute = 5;
}
