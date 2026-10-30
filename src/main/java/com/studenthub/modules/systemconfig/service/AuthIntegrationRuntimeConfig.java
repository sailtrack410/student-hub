package com.studenthub.modules.systemconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.integration.jw.KingosoftJwClient;
import com.studenthub.integration.mail.MailProperties;
import com.studenthub.integration.mail.MailRuntimeRefresher;
import com.studenthub.integration.qq.QqApiIdentityProvider;
import com.studenthub.integration.qq.QqProperties;
import com.studenthub.integration.wechat.WechatApiIdentityProvider;
import com.studenthub.integration.wechat.WechatProperties;
import com.studenthub.modules.jwauth.config.JwProperties;
import com.studenthub.modules.file.config.FileProperties;
import com.studenthub.modules.jwauth.security.JwSessionCipher;
import com.studenthub.modules.location.config.AmapProperties;
import com.studenthub.modules.rag.config.RagDataSourceHolder;
import com.studenthub.modules.rag.config.RagProperties;
import com.studenthub.modules.rag.config.RagSchemaInitializer;
import com.studenthub.modules.systemconfig.entity.SysConfigEntity;
import com.studenthub.modules.systemconfig.mapper.SysConfigMapper;
import com.studenthub.modules.systemconfig.security.ConfigSecretCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 外部集成与文件存储（QQ / 微信 / 邮箱 / 教务 / 高德 / RAG / local-MinIO-OSS）配置的
 * “数据库 + 环境变量”合并解析与热更新。
 *
 * <p>优先级：</p>
 * <ol>
 *   <li>数据库有值（含加密密钥）→ 数据库；</li>
 *   <li>数据库为空且管理员从未编辑过（updated_by=0）→ 环境变量；</li>
 *   <li>数据库为空且管理员编辑过 → 显式清空，不再回退环境变量；</li>
 *   <li>都没有 → 未配置。</li>
 * </ol>
 *
 * <p>启动时把环境变量中的非空值迁移到空的种子行，随后数据库成为可管理来源。
 * 管理端保存事务提交后，监听 {@link SystemConfigChangedEvent} 热更新属性 Bean、
 * OAuth RestClient 和 JavaMailSender，无需重启。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthIntegrationRuntimeConfig {

    public static final String SOURCE_DATABASE = "DATABASE";
    public static final String SOURCE_ENVIRONMENT = "ENVIRONMENT";
    public static final String SOURCE_UNSET = "UNSET";
    public static final String SOURCE_ERROR = "ERROR";

    private final SysConfigMapper configMapper;
    private final ConfigSecretCipher secretCipher;
    private final Environment environment;
    private final QqProperties qqProperties;
    private final WechatProperties wechatProperties;
    private final MailProperties mailProperties;
    private final QqApiIdentityProvider qqApiIdentityProvider;
    private final WechatApiIdentityProvider wechatApiIdentityProvider;
    private final MailRuntimeRefresher mailRuntimeRefresher;
    private final JwProperties jwProperties;
    private final KingosoftJwClient kingosoftJwClient;
    private final JwSessionCipher jwSessionCipher;
    private final AmapProperties amapProperties;
    private final RagProperties ragProperties;
    private final RagDataSourceHolder ragDataSourceHolder;
    private final RagSchemaInitializer ragSchemaInitializer;
    private final FileProperties fileProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        bootstrapBlankRowsFromEnvironment();
        reload();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConfigChanged(SystemConfigChangedEvent event) {
        log.info("配置已变更，开始热更新外部集成运行时配置, reason={}",
                event == null ? "" : event.reason());
        try {
            reload();
        } catch (Exception exception) {
            // 事务已提交，热更新失败不能把已保存的配置回滚；记录后可再次保存/重启恢复。
            log.error("外部集成配置热更新失败，当前运行时配置可能仍为旧值", exception);
        }
    }

    /**
     * 只在启动时执行：把当前环境变量中的非空值写入后续从未编辑过的空白种子行。
     * 管理员编辑过的行（updated_by != 0）即使为空也不回填，保证“清空”语义。
     */
    public synchronized void bootstrapBlankRowsFromEnvironment() {
        List<SysConfigEntity> rows = loadRows();
        int migrated = 0;
        for (SysConfigEntity row : rows) {
            if (StringUtils.hasText(row.getConfigValue())
                    || (row.getUpdatedBy() != null && row.getUpdatedBy() != 0)) {
                continue;
            }
            String envName = AuthIntegrationConfigKeys.envProperty(row.getConfigKey());
            if (envName == null) {
                continue;
            }
            String envValue = environment.getProperty(envName);
            if (!StringUtils.hasText(envValue)) {
                continue;
            }
            String stored = envValue;
            if (Boolean.TRUE.equals(row.getIsSecret())) {
                if (!secretCipher.available()) {
                    log.warn("CONFIG_SECRET_KEY 未配置，跳过环境变量密钥迁移: {}", row.getConfigKey());
                    continue;
                }
                stored = secretCipher.encrypt(row.getConfigKey(), envValue);
            }
            SysConfigEntity update = new SysConfigEntity();
            update.setId(row.getId());
            update.setConfigValue(stored);
            update.setUpdatedBy(0L);
            update.setUpdatedAt(LocalDateTime.now());
            update.setVersion(row.getVersion());
            configMapper.updateById(update);
            migrated++;
        }
        if (migrated > 0) {
            log.info("已将 {} 项环境变量配置迁移到管理端配置中心", migrated);
        }
    }

    /** 读取全部认证配置的有效值（数据库/环境变量/未配置/错误）。 */
    public Map<String, EffectiveConfig> effectiveConfigs() {
        Map<String, SysConfigEntity> rows = loadRows().stream()
                .collect(Collectors.toMap(SysConfigEntity::getConfigKey, Function.identity(), (a, b) -> a));
        Map<String, EffectiveConfig> result = new LinkedHashMap<>();
        for (String key : AuthIntegrationConfigKeys.ALL_KEYS) {
            result.put(key, resolve(key, rows.get(key)));
        }
        return result;
    }

    public EffectiveConfig effectiveConfig(String key) {
        Map<String, SysConfigEntity> rows = loadRows().stream()
                .collect(Collectors.toMap(SysConfigEntity::getConfigKey, Function.identity(), (a, b) -> a));
        return resolve(key, rows.get(key));
    }

    /** 把有效配置应用到运行时属性 Bean、OAuth 客户端与邮件发送器。 */
    public synchronized void reload() {
        Map<String, EffectiveConfig> values = effectiveConfigs();

        String qqMode = normalizeMode(value(values, AuthIntegrationConfigKeys.QQ_MODE), "mock");
        qqProperties.setMode(qqMode);
        qqProperties.setClientId(text(values, AuthIntegrationConfigKeys.QQ_CLIENT_ID, qqProperties.getClientId()));
        qqProperties.setClientSecret(text(values, AuthIntegrationConfigKeys.QQ_CLIENT_SECRET,
                qqProperties.getClientSecret()));
        qqProperties.setApiBaseUrl(defaultIfBlank(
                text(values, AuthIntegrationConfigKeys.QQ_API_BASE_URL, qqProperties.getApiBaseUrl()),
                "https://graph.qq.com"));
        qqProperties.setDefaultCampusId(longValue(values, AuthIntegrationConfigKeys.QQ_DEFAULT_CAMPUS_ID,
                qqProperties.getDefaultCampusId()));
        qqProperties.setConnectTimeoutSeconds(intValue(values, AuthIntegrationConfigKeys.QQ_CONNECT_TIMEOUT_SECONDS,
                qqProperties.getConnectTimeoutSeconds()));
        qqProperties.setReadTimeoutSeconds(intValue(values, AuthIntegrationConfigKeys.QQ_READ_TIMEOUT_SECONDS,
                qqProperties.getReadTimeoutSeconds()));
        qqApiIdentityProvider.reload();

        String wechatMode = normalizeMode(value(values, AuthIntegrationConfigKeys.WECHAT_MODE), "real");
        wechatProperties.setMode(wechatMode);
        wechatProperties.setAppId(text(values, AuthIntegrationConfigKeys.WECHAT_APP_ID,
                wechatProperties.getAppId()));
        wechatProperties.setAppSecret(text(values, AuthIntegrationConfigKeys.WECHAT_APP_SECRET,
                wechatProperties.getAppSecret()));
        wechatProperties.setApiBaseUrl(defaultIfBlank(
                text(values, AuthIntegrationConfigKeys.WECHAT_API_BASE_URL, wechatProperties.getApiBaseUrl()),
                "https://api.weixin.qq.com"));
        wechatProperties.setDefaultCampusId(longValue(values, AuthIntegrationConfigKeys.WECHAT_DEFAULT_CAMPUS_ID,
                wechatProperties.getDefaultCampusId()));
        wechatProperties.setConnectTimeoutSeconds(intValue(values,
                AuthIntegrationConfigKeys.WECHAT_CONNECT_TIMEOUT_SECONDS,
                wechatProperties.getConnectTimeoutSeconds()));
        wechatProperties.setReadTimeoutSeconds(intValue(values, AuthIntegrationConfigKeys.WECHAT_READ_TIMEOUT_SECONDS,
                wechatProperties.getReadTimeoutSeconds()));
        wechatApiIdentityProvider.reload();

        mailProperties.setEnabled(booleanValue(values, AuthIntegrationConfigKeys.MAIL_ENABLED,
                mailProperties.isEnabled()));
        mailProperties.setHost(text(values, AuthIntegrationConfigKeys.MAIL_HOST, mailProperties.getHost()));
        mailProperties.setPort(intValue(values, AuthIntegrationConfigKeys.MAIL_PORT, mailProperties.getPort()));
        mailProperties.setUsername(text(values, AuthIntegrationConfigKeys.MAIL_USERNAME,
                mailProperties.getUsername()));
        mailProperties.setPassword(text(values, AuthIntegrationConfigKeys.MAIL_PASSWORD,
                mailProperties.getPassword()));
        mailProperties.setFrom(text(values, AuthIntegrationConfigKeys.MAIL_FROM, mailProperties.getFrom()));
        mailProperties.setFromName(defaultIfBlank(
                text(values, AuthIntegrationConfigKeys.MAIL_FROM_NAME, mailProperties.getFromName()),
                "绵城一码通"));
        mailProperties.setSslEnabled(booleanValue(values, AuthIntegrationConfigKeys.MAIL_SSL_ENABLED,
                mailProperties.isSslEnabled()));
        mailProperties.setStarttlsEnabled(booleanValue(values, AuthIntegrationConfigKeys.MAIL_STARTTLS_ENABLED,
                mailProperties.isStarttlsEnabled()));
        mailProperties.setConnectTimeoutMs(intValue(values, AuthIntegrationConfigKeys.MAIL_CONNECT_TIMEOUT_MS,
                mailProperties.getConnectTimeoutMs()));
        mailProperties.setReadTimeoutMs(intValue(values, AuthIntegrationConfigKeys.MAIL_READ_TIMEOUT_MS,
                mailProperties.getReadTimeoutMs()));
        mailProperties.setWriteTimeoutMs(intValue(values, AuthIntegrationConfigKeys.MAIL_WRITE_TIMEOUT_MS,
                mailProperties.getWriteTimeoutMs()));
        mailProperties.setDevReturnCode(booleanValue(values, AuthIntegrationConfigKeys.MAIL_DEV_RETURN_CODE,
                mailProperties.isDevReturnCode()));
        mailRuntimeRefresher.refresh();

        reloadJw(values);
        reloadAmap(values);
        reloadRag(values);
        reloadFile(values);

        log.info("QQ/微信/邮箱配置热更新完成, qqMode={}, wechatMode={}, mailEnabled={}, mailHostConfigured={}",
                qqMode, wechatMode, mailProperties.isEnabled(), StringUtils.hasText(mailProperties.getHost()));
        log.info("教务/高德/RAG/文件存储配置热更新完成, jwBase={}, amapConfigured={}, ragEnabled={}, "
                        + "ragJdbcConfigured={}, storage={}",
                StringUtils.hasText(jwProperties.getJwBaseUrl()), StringUtils.hasText(amapProperties.getWebServiceKey()),
                ragProperties.isEnabled(), StringUtils.hasText(ragProperties.getJdbcUrl()),
                fileProperties.normalizedStorage());
    }

    private void reloadJw(Map<String, EffectiveConfig> values) {
        jwProperties.setSsoBaseUrl(defaultIfBlank(
                text(values, AuthIntegrationConfigKeys.JW_SSO_BASE_URL, jwProperties.getSsoBaseUrl()),
                "https://sso.mycc.edu.cn"));
        jwProperties.setJwBaseUrl(defaultIfBlank(
                text(values, AuthIntegrationConfigKeys.JW_JW_BASE_URL, jwProperties.getJwBaseUrl()),
                "https://jwgl.mycc.edu.cn"));
        jwProperties.setCasService(defaultIfBlank(
                text(values, AuthIntegrationConfigKeys.JW_CAS_SERVICE, jwProperties.getCasService()),
                "http://jwgl.mycc.edu.cn/caslogin"));
        jwProperties.setRsaModulus(defaultIfBlank(
                text(values, AuthIntegrationConfigKeys.JW_RSA_MODULUS, jwProperties.getRsaModulus()),
                jwProperties.getRsaModulus()));
        jwProperties.setRsaExponent(defaultIfBlank(
                text(values, AuthIntegrationConfigKeys.JW_RSA_EXPONENT, jwProperties.getRsaExponent()),
                "010001"));
        jwProperties.setEncryptKey(text(values, AuthIntegrationConfigKeys.JW_ENCRYPT_KEY,
                jwProperties.getEncryptKey()));
        jwProperties.setSessionTtlHours(intValue(values, AuthIntegrationConfigKeys.JW_SESSION_TTL_HOURS,
                jwProperties.getSessionTtlHours()));
        jwProperties.setConnectTimeoutSeconds(intValue(values,
                AuthIntegrationConfigKeys.JW_CONNECT_TIMEOUT_SECONDS, jwProperties.getConnectTimeoutSeconds()));
        jwProperties.setReadTimeoutSeconds(intValue(values,
                AuthIntegrationConfigKeys.JW_READ_TIMEOUT_SECONDS, jwProperties.getReadTimeoutSeconds()));
        jwProperties.setAuthRateLimitPerMinute(intValue(values,
                AuthIntegrationConfigKeys.JW_AUTH_RATE_LIMIT_PER_MINUTE, jwProperties.getAuthRateLimitPerMinute()));
        kingosoftJwClient.reload();
        jwSessionCipher.reload();
    }

    private void reloadAmap(Map<String, EffectiveConfig> values) {
        amapProperties.setWebServiceKey(text(values, AuthIntegrationConfigKeys.AMAP_WEB_SERVICE_KEY,
                amapProperties.getWebServiceKey()));
        amapProperties.setBaseUrl(defaultIfBlank(
                text(values, AuthIntegrationConfigKeys.AMAP_BASE_URL, amapProperties.getBaseUrl()),
                "https://restapi.amap.com"));
        amapProperties.setConnectTimeoutSeconds(intValue(values,
                AuthIntegrationConfigKeys.AMAP_CONNECT_TIMEOUT_SECONDS,
                amapProperties.getConnectTimeoutSeconds()));
        amapProperties.setReadTimeoutSeconds(intValue(values,
                AuthIntegrationConfigKeys.AMAP_READ_TIMEOUT_SECONDS,
                amapProperties.getReadTimeoutSeconds()));
    }

    private void reloadRag(Map<String, EffectiveConfig> values) {
        boolean oldEnabled = ragProperties.isEnabled();
        String oldUrl = ragProperties.getJdbcUrl();
        String oldUsername = ragProperties.getUsername();
        String oldPassword = ragProperties.getPassword();
        ragProperties.setEnabled(booleanValue(values, AuthIntegrationConfigKeys.RAG_ENABLED, oldEnabled));
        ragProperties.setJdbcUrl(text(values, AuthIntegrationConfigKeys.RAG_JDBC_URL, oldUrl));
        ragProperties.setUsername(text(values, AuthIntegrationConfigKeys.RAG_USERNAME, oldUsername));
        ragProperties.setPassword(text(values, AuthIntegrationConfigKeys.RAG_PASSWORD, oldPassword));
        ragProperties.setMaxDocumentSizeMb(intValue(values,
                AuthIntegrationConfigKeys.RAG_MAX_DOCUMENT_SIZE_MB, ragProperties.getMaxDocumentSizeMb()));
        ragProperties.setMaxParsedCharacters(intValue(values,
                AuthIntegrationConfigKeys.RAG_MAX_PARSED_CHARACTERS, ragProperties.getMaxParsedCharacters()));
        ragProperties.setEmbeddingBatchSize(intValue(values,
                AuthIntegrationConfigKeys.RAG_EMBEDDING_BATCH_SIZE, ragProperties.getEmbeddingBatchSize()));
        ragProperties.setLexicalCandidateLimit(intValue(values,
                AuthIntegrationConfigKeys.RAG_LEXICAL_CANDIDATE_LIMIT, ragProperties.getLexicalCandidateLimit()));
        ragProperties.setVectorCandidateLimit(intValue(values,
                AuthIntegrationConfigKeys.RAG_VECTOR_CANDIDATE_LIMIT, ragProperties.getVectorCandidateLimit()));
        ragProperties.setLexicalMinSimilarity(doubleValue(values,
                AuthIntegrationConfigKeys.RAG_LEXICAL_MIN_SIMILARITY, ragProperties.getLexicalMinSimilarity()));
        ragProperties.setRrfK(intValue(values, AuthIntegrationConfigKeys.RAG_RRF_K, ragProperties.getRrfK()));
        // 只有连接目标或启用状态变化才重建连接池；调召回参数不应打断 RAG 在线请求。
        boolean connectionChanged = oldEnabled != ragProperties.isEnabled()
                || !java.util.Objects.equals(oldUrl, ragProperties.getJdbcUrl())
                || !java.util.Objects.equals(oldUsername, ragProperties.getUsername())
                || !java.util.Objects.equals(oldPassword, ragProperties.getPassword());
        if (connectionChanged) {
            ragDataSourceHolder.invalidate();
            ragSchemaInitializer.initializeIfEnabled();
        }
    }

    private void reloadFile(Map<String, EffectiveConfig> values) {
        String storage = text(values, AuthIntegrationConfigKeys.FILE_STORAGE,
                fileProperties.normalizedStorage()).trim().toLowerCase();
        if (!java.util.Set.of("local", "minio", "oss").contains(storage)) {
            log.warn("文件存储实现 {} 不合法，保留当前值 {}", storage, fileProperties.normalizedStorage());
            storage = fileProperties.normalizedStorage();
        }
        fileProperties.setStorage(storage);
        fileProperties.setStorageDir(defaultIfBlank(
                text(values, AuthIntegrationConfigKeys.FILE_STORAGE_DIR, fileProperties.getStorageDir()),
                "./data/files"));
        fileProperties.setSignSecret(defaultIfBlank(
                text(values, AuthIntegrationConfigKeys.FILE_SIGN_SECRET, fileProperties.getSignSecret()),
                "dev-sign-secret-change-me"));
        fileProperties.setMaxFileSizeMb(intValue(values, AuthIntegrationConfigKeys.FILE_MAX_SIZE_MB, 5));
        fileProperties.setViewTtlMinutes(intValue(values, AuthIntegrationConfigKeys.FILE_VIEW_TTL_MINUTES, 30));
        fileProperties.setMinioEndpoint(defaultIfBlank(
                text(values, AuthIntegrationConfigKeys.FILE_MINIO_ENDPOINT, fileProperties.getMinioEndpoint()),
                "http://localhost:9000"));
        fileProperties.setMinioPublicEndpoint(
                text(values, AuthIntegrationConfigKeys.FILE_MINIO_PUBLIC_ENDPOINT,
                        fileProperties.getMinioPublicEndpoint()));
        fileProperties.setMinioAccessKey(
                text(values, AuthIntegrationConfigKeys.FILE_MINIO_ACCESS_KEY, fileProperties.getMinioAccessKey()));
        fileProperties.setMinioSecretKey(
                text(values, AuthIntegrationConfigKeys.FILE_MINIO_SECRET_KEY, fileProperties.getMinioSecretKey()));
        fileProperties.setMinioBucket(defaultIfBlank(
                text(values, AuthIntegrationConfigKeys.FILE_MINIO_BUCKET, fileProperties.getMinioBucket()),
                "student-hub"));
        fileProperties.setOssEndpoint(
                text(values, AuthIntegrationConfigKeys.FILE_OSS_ENDPOINT, fileProperties.getOssEndpoint()));
        fileProperties.setOssPublicEndpoint(
                text(values, AuthIntegrationConfigKeys.FILE_OSS_PUBLIC_ENDPOINT, fileProperties.getOssPublicEndpoint()));
        fileProperties.setOssBucket(
                text(values, AuthIntegrationConfigKeys.FILE_OSS_BUCKET, fileProperties.getOssBucket()));
        fileProperties.setOssAccessKeyId(
                text(values, AuthIntegrationConfigKeys.FILE_OSS_ACCESS_KEY_ID, fileProperties.getOssAccessKeyId()));
        fileProperties.setOssAccessKeySecret(
                text(values, AuthIntegrationConfigKeys.FILE_OSS_ACCESS_KEY_SECRET,
                        fileProperties.getOssAccessKeySecret()));
    }

    private List<SysConfigEntity> loadRows() {
        return configMapper.selectList(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getCampusId, 0L)
                .eq(SysConfigEntity::getStatus, "ENABLED")
                .in(SysConfigEntity::getConfigKey, AuthIntegrationConfigKeys.ALL_KEYS));
    }

    private EffectiveConfig resolve(String key, SysConfigEntity row) {
        boolean secret = isSecretKey(key);
        boolean managed = row != null && row.getUpdatedBy() != null && row.getUpdatedBy() != 0;
        if (row != null && StringUtils.hasText(row.getConfigValue())) {
            String resolved = row.getConfigValue();
            if (secret) {
                if (!secretCipher.available()) {
                    return new EffectiveConfig(key, null, SOURCE_ERROR, true, false,
                            "CONFIG_SECRET_KEY 未配置，无法解密");
                }
                try {
                    resolved = secretCipher.decrypt(key, resolved);
                } catch (Exception exception) {
                    return new EffectiveConfig(key, null, SOURCE_ERROR, true, false,
                            "密钥解密失败，请检查 CONFIG_SECRET_KEY");
                }
            }
            return new EffectiveConfig(key, resolved, SOURCE_DATABASE, secret,
                    StringUtils.hasText(resolved), null);
        }
        if (managed) {
            return new EffectiveConfig(key, "", SOURCE_DATABASE, secret, false, null);
        }
        String envName = AuthIntegrationConfigKeys.envProperty(key);
        String envValue = envName == null ? null : environment.getProperty(envName);
        if (StringUtils.hasText(envValue)) {
            return new EffectiveConfig(key, envValue, SOURCE_ENVIRONMENT, secret, true, null);
        }
        return new EffectiveConfig(key, "", SOURCE_UNSET, secret, false, null);
    }

    private boolean isSecretKey(String key) {
        return AuthIntegrationConfigKeys.isSecretKey(key);
    }

    private String value(Map<String, EffectiveConfig> values, String key) {
        EffectiveConfig config = values.get(key);
        return config == null ? "" : config.value();
    }

    private String text(Map<String, EffectiveConfig> values, String key, String fallback) {
        EffectiveConfig config = values.get(key);
        if (config == null || SOURCE_UNSET.equals(config.source()) || SOURCE_ERROR.equals(config.source())) {
            return fallback;
        }
        return config.value() == null ? "" : config.value();
    }

    private String normalizeMode(String value, String fallback) {
        if ("real".equalsIgnoreCase(value)) {
            return "real";
        }
        if ("mock".equalsIgnoreCase(value)) {
            return "mock";
        }
        return fallback;
    }

    private int intValue(Map<String, EffectiveConfig> values, String key, int fallback) {
        try {
            String value = text(values, key, null);
            return StringUtils.hasText(value) ? Integer.parseInt(value.trim()) : fallback;
        } catch (Exception exception) {
            log.warn("配置 {} 不是合法整数，保留当前值 {}", key, fallback);
            return fallback;
        }
    }

    private long longValue(Map<String, EffectiveConfig> values, String key, Long fallback) {
        try {
            String value = text(values, key, null);
            return StringUtils.hasText(value) ? Long.parseLong(value.trim())
                    : (fallback == null ? 0L : fallback);
        } catch (Exception exception) {
            log.warn("配置 {} 不是合法整数，保留当前值 {}", key, fallback);
            return fallback == null ? 0L : fallback;
        }
    }

    private double doubleValue(Map<String, EffectiveConfig> values, String key, double fallback) {
        try {
            String value = text(values, key, null);
            return StringUtils.hasText(value) ? Double.parseDouble(value.trim()) : fallback;
        } catch (Exception exception) {
            log.warn("配置 {} 不是合法小数，保留当前值 {}", key, fallback);
            return fallback;
        }
    }

    private boolean booleanValue(Map<String, EffectiveConfig> values, String key, boolean fallback) {
        String value = text(values, key, null);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value.trim());
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    public record EffectiveConfig(String key, String value, String source,
                                  boolean secret, boolean configured, String error) {
    }
}
