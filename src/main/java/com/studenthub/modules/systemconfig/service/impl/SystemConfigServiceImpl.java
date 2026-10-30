package com.studenthub.modules.systemconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.mail.MailRuntimeRefresher;
import com.studenthub.integration.storage.RoutingBinaryStorage;
import com.studenthub.integration.storage.StorageProbeResult;
import com.studenthub.modules.location.integration.AmapLocationClient;
import com.studenthub.modules.rag.service.RagConnectivityProbe;
import com.studenthub.integration.wechat.WechatCredentialProbe;
import com.studenthub.modules.appversion.entity.AppVersionEntity;
import com.studenthub.modules.appversion.mapper.AppVersionMapper;
import com.studenthub.modules.audit.service.AuditLogService;
import com.studenthub.modules.file.config.FileProperties;
import com.studenthub.modules.officialfeed.entity.OfficialFeedSourceEntity;
import com.studenthub.modules.officialfeed.mapper.OfficialFeedSourceMapper;
import com.studenthub.modules.systemconfig.controller.dto.*;
import com.studenthub.modules.systemconfig.security.ConfigSecretCipher;
import com.studenthub.modules.systemconfig.service.AuthIntegrationConfigKeys;
import com.studenthub.modules.systemconfig.service.AuthIntegrationRuntimeConfig;
import com.studenthub.modules.systemconfig.service.ScheduledTaskRegistry;
import com.studenthub.modules.systemconfig.service.SystemConfigChangedEvent;
import com.studenthub.modules.systemconfig.entity.SysConfigEntity;
import com.studenthub.modules.systemconfig.entity.SysFeatureRolloutEntity;
import com.studenthub.modules.systemconfig.mapper.SysConfigMapper;
import com.studenthub.modules.systemconfig.mapper.SysFeatureRolloutMapper;
import com.studenthub.modules.systemconfig.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SysConfigMapper configMapper;
    private final AuditLogService auditLogService;
    private final Environment environment;
    private final FileProperties fileProperties;
    private final org.springframework.beans.factory.ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisProvider;
    private final OfficialFeedSourceMapper officialFeedSourceMapper;
    private final AppVersionMapper appVersionMapper;
    private final ScheduledTaskRegistry scheduledTaskRegistry;
    private final SysFeatureRolloutMapper featureRolloutMapper;
    private final ConfigSecretCipher secretCipher;
    private final AuthIntegrationRuntimeConfig authIntegrationRuntimeConfig;
    private final ApplicationEventPublisher eventPublisher;
    private final WechatCredentialProbe wechatCredentialProbe;
    private final MailRuntimeRefresher mailRuntimeRefresher;
    private final AmapLocationClient amapLocationClient;
    private final RagConnectivityProbe ragConnectivityProbe;
    private final RoutingBinaryStorage routingBinaryStorage;

    private static final Set<String> FEATURE_KEYS = Set.of(
            "feature.maintenance", "feature.register", "feature.password_login",
            "feature.wechat_login", "feature.qq_login", "feature.ai", "feature.rag",
            "feature.community", "feature.homework", "feature.push",
            "feature.electricity", "feature.location", "feature.app_update",
            "feature.community_ads");

    private static final Map<String, String> FEATURE_NAMES = Map.ofEntries(
            Map.entry("feature.maintenance", "维护模式"),
            Map.entry("feature.register", "用户注册"),
            Map.entry("feature.password_login", "密码登录"),
            Map.entry("feature.wechat_login", "微信登录"),
            Map.entry("feature.qq_login", "QQ 登录"),
            Map.entry("feature.ai", "AI 助手"),
            Map.entry("feature.rag", "RAG 知识库"),
            Map.entry("feature.community", "校园社区"),
            Map.entry("feature.homework", "作业中心"),
            Map.entry("feature.push", "推送通知"),
            Map.entry("feature.electricity", "电费查询"),
            Map.entry("feature.location", "定位与地点"),
            Map.entry("feature.app_update", "App 更新检查"),
            Map.entry("feature.community_ads", "社区广告"));

    private static final Set<String> VALUE_TYPES = Set.of("STRING", "BOOLEAN", "INT", "LONG", "JSON");

    @Override
    public List<AdminConfigItemResponse> list(String group, Long campusId) {
        LambdaQueryWrapper<SysConfigEntity> wrapper = new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getStatus, "ENABLED")
                .orderByAsc(SysConfigEntity::getConfigGroup)
                .orderByAsc(SysConfigEntity::getConfigKey);
        if (StringUtils.hasText(group)) wrapper.eq(SysConfigEntity::getConfigGroup, group);
        if (campusId != null && campusId > 0) {
            wrapper.and(w -> w.eq(SysConfigEntity::getCampusId, 0L)
                    .or().eq(SysConfigEntity::getCampusId, campusId));
        } else {
            wrapper.eq(SysConfigEntity::getCampusId, 0L);
        }
        List<SysConfigEntity> rows = configMapper.selectList(wrapper);
        Map<String, AuthIntegrationRuntimeConfig.EffectiveConfig> effective = authIntegrationRuntimeConfig.effectiveConfigs();
        return rows.stream().map(entity -> {
            if (!AuthIntegrationConfigKeys.isManagedKey(entity.getConfigKey())) {
                return AdminConfigItemResponse.from(entity);
            }
            AuthIntegrationRuntimeConfig.EffectiveConfig value = effective.get(entity.getConfigKey());
            if (value == null) {
                return AdminConfigItemResponse.from(entity);
            }
            return AdminConfigItemResponse.from(entity, value.value(), value.source(), value.configured(), value.error());
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AdminConfigItemResponse> update(AdminConfigUpdateRequest request, Long operatorId, String operatorName) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "没有要保存的配置");
        }
        List<Map<String, Object>> before = new ArrayList<>();
        List<Map<String, Object>> after = new ArrayList<>();
        for (AdminConfigUpdateRequest.Item item : request.items()) {
            if (!StringUtils.hasText(item.key())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "配置键不能为空");
            }
            Long campusId = item.campusId() == null ? 0L : item.campusId();
            SysConfigEntity entity = configMapper.selectOne(new LambdaQueryWrapper<SysConfigEntity>()
                    .eq(SysConfigEntity::getCampusId, campusId)
                    .eq(SysConfigEntity::getConfigKey, item.key())
                    .last("LIMIT 1"));
            if (entity == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "配置项不存在: " + item.key());
            }
            boolean secret = Boolean.TRUE.equals(entity.getIsSecret());
            String storedValue;
            if (secret) {
                if (!secretCipher.available()) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                            "CONFIG_SECRET_KEY 未配置，暂不能保存密钥类配置: " + item.key());
                }
                storedValue = StringUtils.hasText(item.value())
                        ? secretCipher.encrypt(item.key(), item.value())
                        : "";
                if (storedValue.length() > 8192) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "密钥值过长: " + item.key());
                }
            } else {
                storedValue = item.value() == null ? "" : item.value();
                validateValue(entity.getValueType(), storedValue, item.key());
            }
            validateIntegrationConfigValue(item.key(),
                    secret ? (item.value() == null ? "" : item.value()) : storedValue);
            Map<String, Object> oldValue = snapshot(entity);
            SysConfigEntity update = new SysConfigEntity();
            update.setId(entity.getId());
            update.setConfigValue(storedValue);
            update.setUpdatedBy(operatorId);
            update.setUpdatedAt(LocalDateTime.now());
            update.setVersion(entity.getVersion());
            configMapper.updateById(update);
            before.add(oldValue);
            Map<String, Object> newValue = snapshot(entity);
            newValue.put("value", secret
                    ? (StringUtils.hasText(storedValue) ? "******" : "")
                    : storedValue);
            after.add(newValue);
        }
        auditLogService.record(operatorId, operatorName, "CONFIG_UPDATE", "SYSTEM_CONFIG",
                String.valueOf(request.items().size()), before, after);
        eventPublisher.publishEvent(new SystemConfigChangedEvent("admin:" + operatorId));
        return list(null, request.items().get(0).campusId());
    }

    @Override
    public java.util.Map<String, Boolean> effectiveFeatures(Long campusId) {
        return effectiveFeatures(campusId, null);
    }

    @Override
    public java.util.Map<String, Boolean> effectiveFeatures(Long campusId, Long userId) {
        java.util.Map<String, Boolean> flags = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> rollout = rolloutPercents(campusId);
        for (String key : FEATURE_KEYS) {
            boolean fallback = !"feature.maintenance".equals(key);
            boolean enabled = getBoolean(key, campusId, fallback);
            if (enabled) {
                int percent = rollout.getOrDefault(key, 100);
                if (percent <= 0) {
                    enabled = false;
                } else if (percent < 100) {
                    // 未登录请求不进入灰度桶；已登录用户按 userId + 功能键稳定分桶，刷新不跳变。
                    enabled = userId != null && Math.floorMod(Objects.hash(userId, key), 100) < percent;
                }
            }
            flags.put(key, enabled);
        }
        return flags;
    }

    private java.util.Map<String, Integer> rolloutPercents(Long campusId) {
        long effectiveCampus = campusId == null ? 0L : campusId;
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        try {
            List<SysFeatureRolloutEntity> rows = featureRolloutMapper.selectList(
                    new LambdaQueryWrapper<SysFeatureRolloutEntity>()
                            .in(SysFeatureRolloutEntity::getFeatureKey, FEATURE_KEYS)
                            .and(w -> w.eq(SysFeatureRolloutEntity::getCampusId, 0L)
                                    .or().eq(SysFeatureRolloutEntity::getCampusId, effectiveCampus)));
            // 先放校区默认，再用当前校区覆盖。
            rows.stream().filter(r -> r.getCampusId() != null && r.getCampusId() == 0L)
                    .forEach(r -> out.put(r.getFeatureKey(), clampPercent(r.getRolloutPercent())));
            rows.stream().filter(r -> r.getCampusId() != null && r.getCampusId() == effectiveCampus && effectiveCampus != 0L)
                    .forEach(r -> out.put(r.getFeatureKey(), clampPercent(r.getRolloutPercent())));
        } catch (RuntimeException exception) {
            log.warn("读取功能开关灰度比例失败，按全量处理: {}", exception.getMessage());
        }
        return out;
    }

    private int clampPercent(Integer percent) {
        if (percent == null) return 100;
        return Math.max(0, Math.min(100, percent));
    }

    @Override
    @Transactional
    public void updateRollout(String featureKey, Long campusId, int percent, Long operatorId) {
        if (!StringUtils.hasText(featureKey) || !FEATURE_KEYS.contains(featureKey)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不支持的功能开关: " + featureKey);
        }
        if (percent < 0 || percent > 100) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "灰度比例必须在 0-100 之间");
        }
        long effectiveCampus = campusId == null ? 0L : campusId;
        SysFeatureRolloutEntity row = featureRolloutMapper.selectOne(
                new LambdaQueryWrapper<SysFeatureRolloutEntity>()
                        .eq(SysFeatureRolloutEntity::getFeatureKey, featureKey)
                        .eq(SysFeatureRolloutEntity::getCampusId, effectiveCampus)
                        .last("LIMIT 1"));
        if (row == null) {
            row = new SysFeatureRolloutEntity();
            row.setFeatureKey(featureKey);
            row.setCampusId(effectiveCampus);
            row.setRolloutPercent(percent);
            row.setUpdatedBy(operatorId);
            row.setUpdatedAt(LocalDateTime.now());
            featureRolloutMapper.insert(row);
        } else {
            row.setRolloutPercent(percent);
            row.setUpdatedBy(operatorId);
            row.setUpdatedAt(LocalDateTime.now());
            featureRolloutMapper.updateById(row);
        }
    }

    @Override
    public List<AdminFeatureFlagResponse> features(Long campusId) {
        java.util.Map<String, Integer> rollout = rolloutPercents(campusId);
        return list("feature", campusId).stream()
                .filter(item -> FEATURE_KEYS.contains(item.key()))
                .map(item -> new AdminFeatureFlagResponse(item.key(),
                        FEATURE_NAMES.getOrDefault(item.key(), item.key()),
                        "true".equalsIgnoreCase(item.value()) || "1".equals(item.value()),
                        rollout.getOrDefault(item.key(), 100),
                        item.description()))
                .toList();
    }

    @Override
    public AdminSystemHealthResponse health() {
        List<AdminSystemHealthResponse.Component> components = new ArrayList<>();
        components.add(checkDb());
        components.add(checkRedis());
        components.add(checkStorage());
        components.add(checkDisk());
        components.add(checkScheduledTasks());
        components.add(checkAi());
        components.add(checkJw());
        components.add(checkOfficialFeed());
        components.add(checkVersion());
        // 核心依赖不可用 → DOWN；核心降级或可选依赖异常 → DEGRADED；可选依赖“未配置”不阻断整体状态。
        Set<String> required = Set.of("MySQL", "Redis", "文件存储", "磁盘", "定时任务");
        boolean requiredDown = components.stream().anyMatch(c -> required.contains(c.name())
                && "DOWN".equals(c.status()));
        boolean requiredNotUp = components.stream().anyMatch(c -> required.contains(c.name())
                && !"UP".equals(c.status()));
        boolean optionalDown = components.stream().anyMatch(c -> !required.contains(c.name())
                && "DOWN".equals(c.status()));
        String overall = requiredDown ? "DOWN" : (requiredNotUp || optionalDown ? "DEGRADED" : "UP");
        return new AdminSystemHealthResponse(overall, LocalDateTime.now(), components);
    }

    @Override
    public List<AdminIntegrationStatusResponse> integrations() {
        List<AdminIntegrationStatusResponse> list = new ArrayList<>();
        Map<String, AuthIntegrationRuntimeConfig.EffectiveConfig> auth =
                authIntegrationRuntimeConfig.effectiveConfigs();
        String wechatMode = authValue(auth, AuthIntegrationConfigKeys.WECHAT_MODE, "real");
        boolean wechatReal = "real".equalsIgnoreCase(wechatMode);
        boolean wechatConfigured = hasText(authValue(auth, AuthIntegrationConfigKeys.WECHAT_APP_ID, ""))
                && hasText(authValue(auth, AuthIntegrationConfigKeys.WECHAT_APP_SECRET, ""));
        list.add(new AdminIntegrationStatusResponse("wechat", "微信登录",
                !wechatReal ? "DISABLED" : (wechatConfigured ? "CONFIGURED" : "NOT_CONFIGURED"),
                wechatMode, true,
                !wechatReal ? "mock 模式，仅本地联调，不访问微信平台"
                        : (wechatConfigured ? "AppID/AppSecret 已配置"
                        : "缺少微信 AppID / AppSecret")));
        String qqMode = authValue(auth, AuthIntegrationConfigKeys.QQ_MODE, "mock");
        boolean qqReal = "real".equalsIgnoreCase(qqMode);
        // 移动端 SDK 流程服务端只需 APP ID 校验 token 归属；APP KEY 仅网站授权码流程才需要。
        boolean qqConfigured = hasText(authValue(auth, AuthIntegrationConfigKeys.QQ_CLIENT_ID, ""));
        list.add(new AdminIntegrationStatusResponse("qq", "QQ 登录",
                !qqReal ? "DISABLED" : (qqConfigured ? "CONFIGURED" : "NOT_CONFIGURED"),
                qqMode, true,
                !qqReal ? "mock 模式，仅本地联调，不访问 QQ 平台"
                        : (qqConfigured ? "QQ APP ID 已配置（APP KEY 供网站授权码流程使用）"
                        : "缺少 QQ APP ID")));
        boolean mailEnabled = Boolean.parseBoolean(authValue(auth, AuthIntegrationConfigKeys.MAIL_ENABLED, "false"));
        boolean mailConfigured = hasText(authValue(auth, AuthIntegrationConfigKeys.MAIL_HOST, ""))
                && (hasText(authValue(auth, AuthIntegrationConfigKeys.MAIL_FROM, ""))
                || hasText(authValue(auth, AuthIntegrationConfigKeys.MAIL_USERNAME, "")));
        list.add(new AdminIntegrationStatusResponse("mail", "邮箱 SMTP",
                !mailEnabled ? "DISABLED" : (mailConfigured ? "CONFIGURED" : "NOT_CONFIGURED"),
                mailEnabled ? "SMTP" : "DISABLED", true,
                !mailEnabled ? "邮件发信未启用"
                        : (mailConfigured ? "SMTP 主机与发件人已配置"
                        : "缺少 SMTP 主机或发件人")));
        String jwBase = authValue(auth, AuthIntegrationConfigKeys.JW_JW_BASE_URL, "");
        list.add(new AdminIntegrationStatusResponse("jw", "教务系统（青果）", hasText(jwBase) ? "CONFIGURED" : "NOT_CONFIGURED",
                jwBase, true, "教务 SSO/JW 地址可在管理端修改，保存后登录链路热更新"));
        String amap = authValue(auth, AuthIntegrationConfigKeys.AMAP_WEB_SERVICE_KEY, "");
        list.add(new AdminIntegrationStatusResponse("amap", "高德地图", hasText(amap) ? "CONFIGURED" : "NOT_CONFIGURED",
                "web-service", true, hasText(amap) ? "Web 服务 Key 已配置，可测试地址可用性" : "缺少高德 Web 服务 Key"));
        String storage = fileProperties.normalizedStorage();
        boolean storageConfigured = storageConfigured();
        list.add(new AdminIntegrationStatusResponse("storage", "文件存储", storageConfigured ? "CONFIGURED" : "NOT_CONFIGURED",
                storage, false, storageConfigured ? storageDetail() : storageMissingDetail()));
        boolean aiEnabled = Boolean.parseBoolean(environment.getProperty("studenthub.ai.enabled", "false"));
        boolean aiKey = hasText(environment.getProperty("studenthub.ai.api-key"));
        list.add(new AdminIntegrationStatusResponse("ai", "AI 供应商", aiEnabled && aiKey ? "CONFIGURED"
                        : aiEnabled ? "ERROR" : "DISABLED",
                environment.getProperty("studenthub.ai.model", ""), false,
                aiKey ? "API Key 已配置" : "未配置 AI_API_KEY"));
        boolean ragEnabled = Boolean.parseBoolean(authValue(auth, AuthIntegrationConfigKeys.RAG_ENABLED, "false"));
        boolean ragConfigured = hasText(authValue(auth, AuthIntegrationConfigKeys.RAG_JDBC_URL, ""));
        list.add(new AdminIntegrationStatusResponse("rag", "RAG 知识库",
                !ragEnabled ? "DISABLED" : (ragConfigured ? "CONFIGURED" : "NOT_CONFIGURED"),
                authValue(auth, AuthIntegrationConfigKeys.RAG_JDBC_URL, ""), true,
                !ragEnabled ? "RAG 未启用" : (ragConfigured
                        ? "RAG 独立 PostgreSQL 已配置，可测试连通性；嵌入模型锁定见 AI 默认模型"
                        : "缺少 RAG JDBC 地址")));
        long feedSources = countEnabledFeedSources();
        list.add(new AdminIntegrationStatusResponse("official-feed", "官网内容抓取",
                feedSources > 0 ? "CONFIGURED" : "NOT_CONFIGURED", feedSources + " 个启用来源", false,
                feedSources > 0 ? "已配置 " + feedSources + " 个启用的官网抓取源" : "未配置启用的官网抓取源"));
        return list;
    }

    @Override
    public AdminIntegrationTestResponse testIntegration(String code) {
        String normalized = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        Map<String, AuthIntegrationRuntimeConfig.EffectiveConfig> auth =
                authIntegrationRuntimeConfig.effectiveConfigs();
        boolean aiEnabled = Boolean.parseBoolean(environment.getProperty("studenthub.ai.enabled", "false"));
        return switch (normalized) {
            case "wechat" -> {
                String wechatMode = authValue(auth, AuthIntegrationConfigKeys.WECHAT_MODE, "real");
                if (!"real".equalsIgnoreCase(wechatMode)) {
                    yield integrationResult("wechat", "微信登录", true, true, null,
                            "mock 模式，仅本地联调，不访问微信平台");
                }
                String appId = authValue(auth, AuthIntegrationConfigKeys.WECHAT_APP_ID, "");
                String appSecret = authValue(auth, AuthIntegrationConfigKeys.WECHAT_APP_SECRET, "");
                if (!hasText(appId) || !hasText(appSecret)) {
                    yield integrationResult("wechat", "微信登录", false, false, null,
                            "缺少微信 AppID / AppSecret");
                }
                WechatCredentialProbe.ProbeResult result = wechatCredentialProbe.test(
                        authValue(auth, AuthIntegrationConfigKeys.WECHAT_API_BASE_URL,
                                "https://api.weixin.qq.com"),
                        appId, appSecret,
                        authInt(auth, AuthIntegrationConfigKeys.WECHAT_CONNECT_TIMEOUT_SECONDS, 5),
                        authInt(auth, AuthIntegrationConfigKeys.WECHAT_READ_TIMEOUT_SECONDS, 10));
                yield new AdminIntegrationTestResponse("wechat", "微信登录",
                        result.reachable() ? "CONFIGURED" : "ERROR", true, result.reachable(),
                        result.latencyMs(), result.detail(), LocalDateTime.now());
            }
            case "qq" -> {
                String qqMode = authValue(auth, AuthIntegrationConfigKeys.QQ_MODE, "mock");
                if (!"real".equalsIgnoreCase(qqMode)) {
                    yield integrationResult("qq", "QQ 登录", true, true, null,
                            "mock 模式，仅本地联调，不访问 QQ 平台");
                }
                String clientId = authValue(auth, AuthIntegrationConfigKeys.QQ_CLIENT_ID, "");
                if (!hasText(clientId)) {
                    yield integrationResult("qq", "QQ 登录", false, false, null,
                            "缺少 QQ APP ID（QQ 互联移动应用 APP ID）");
                }
                String baseUrl = authValue(auth, AuthIntegrationConfigKeys.QQ_API_BASE_URL, "https://graph.qq.com");
                yield integrationResult("qq", "QQ 登录", true, false, probe(baseUrl),
                        "QQ APP ID 已配置；移动端登录还需用户真实 accessToken 才能完成身份反查，这里仅探测 API 主机");
            }
            case "mail" -> {
                boolean enabled = Boolean.parseBoolean(authValue(auth, AuthIntegrationConfigKeys.MAIL_ENABLED, "false"));
                if (!enabled) {
                    yield integrationResult("mail", "邮箱 SMTP", true, true, null, "邮件发信未启用");
                }
                boolean configured = hasText(authValue(auth, AuthIntegrationConfigKeys.MAIL_HOST, ""))
                        && (hasText(authValue(auth, AuthIntegrationConfigKeys.MAIL_FROM, ""))
                        || hasText(authValue(auth, AuthIntegrationConfigKeys.MAIL_USERNAME, "")));
                if (!configured) {
                    yield integrationResult("mail", "邮箱 SMTP", false, false, null,
                            "缺少 SMTP 主机或发件人/登录账号");
                }
                MailRuntimeRefresher.ProbeResult result = mailRuntimeRefresher.testConnection();
                yield new AdminIntegrationTestResponse("mail", "邮箱 SMTP",
                        result.reachable() ? "CONFIGURED" : "ERROR", true, result.reachable(),
                        result.latencyMs(), result.detail(), LocalDateTime.now());
            }
            case "jw" -> {
                String base = authValue(auth, AuthIntegrationConfigKeys.JW_JW_BASE_URL, "");
                boolean configured = hasText(base);
                yield integrationResult("jw", "教务系统（青果）", configured, false,
                        configured ? probe(base) : null,
                        configured ? "教务地址已配置，探测教务主机连通性" : "缺少教务 JW 地址");
            }
            case "amap" -> {
                String key = authValue(auth, AuthIntegrationConfigKeys.AMAP_WEB_SERVICE_KEY, "");
                if (!hasText(key)) {
                    yield integrationResult("amap", "高德地图", false, false, null,
                            "缺少高德 Web 服务 Key");
                }
                AmapLocationClient.ProbeResult result = amapLocationClient.probe();
                yield new AdminIntegrationTestResponse("amap", "高德地图",
                        result.reachable() ? "CONFIGURED" : "ERROR", true, result.reachable(),
                        result.latencyMs(), result.detail(), LocalDateTime.now());
            }
            case "storage" -> {
                StorageProbeResult result = routingBinaryStorage.probe();
                String label = switch (result.storage()) {
                    case "minio" -> "MinIO";
                    case "oss" -> "阿里云 OSS";
                    default -> "本地目录";
                };
                yield new AdminIntegrationTestResponse("storage", "文件存储",
                        result.reachable() ? "CONFIGURED" : "ERROR", true, result.reachable(),
                        result.latencyMs(), "当前生效存储 " + label + "：" + result.detail(),
                        LocalDateTime.now());
            }
            case "ai" -> {
                boolean configured = aiEnabled && hasText(environment.getProperty("studenthub.ai.api-key"));
                yield integrationResult("ai", "AI 供应商", configured, !aiEnabled,
                        configured ? probe(environment.getProperty("studenthub.ai.base-url",
                                "https://api.deepseek.com")) : null,
                        !aiEnabled ? "AI_ENABLED=false" : (configured ? "API Key 已配置，探测供应商地址"
                                : "缺少 AI_API_KEY"));
            }
            case "rag" -> {
                boolean enabled = Boolean.parseBoolean(authValue(auth, AuthIntegrationConfigKeys.RAG_ENABLED, "false"));
                if (!enabled) {
                    yield integrationResult("rag", "RAG 知识库", true, true, null, "RAG 未启用");
                }
                boolean configured = hasText(authValue(auth, AuthIntegrationConfigKeys.RAG_JDBC_URL, ""));
                if (!configured) {
                    yield integrationResult("rag", "RAG 知识库", false, false, null, "缺少 RAG JDBC 地址");
                }
                RagConnectivityProbe.ProbeResult result = ragConnectivityProbe.test();
                yield new AdminIntegrationTestResponse("rag", "RAG 知识库",
                        result.reachable() ? "CONFIGURED" : "ERROR", true, result.reachable(),
                        result.latencyMs(), result.detail(), LocalDateTime.now());
            }
            case "official-feed" -> {
                long sources = countEnabledFeedSources();
                String firstUrl = firstEnabledFeedUrl();
                yield integrationResult("official-feed", "官网内容抓取", sources > 0, false,
                        hasText(firstUrl) ? probe(firstUrl) : null,
                        sources > 0 ? "已配置 " + sources + " 个启用抓取源，探测首个来源地址"
                                : "未配置启用的官网抓取源");
            }
            default -> throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "未知集成编码：" + code + "（支持 wechat/qq/mail/jw/amap/storage/ai/rag/official-feed）");
        };
    }

    private AdminIntegrationTestResponse integrationResult(String code, String name, boolean configured,
                                                           boolean disabled, Probe probe, String detail) {
        String status;
        if (disabled) {
            status = "DISABLED";
        } else if (!configured) {
            status = "NOT_CONFIGURED";
        } else if (probe != null && !probe.reachable()) {
            status = "ERROR";
        } else {
            status = "CONFIGURED";
        }
        String finalDetail = detail;
        if (probe != null) {
            finalDetail = detail + "；连通性：" + (probe.reachable() ? "可达（" + probe.latencyMs() + "ms）"
                    : "不可达（" + probe.detail() + "）");
        }
        return new AdminIntegrationTestResponse(code, name, status, configured,
                probe == null ? null : probe.reachable(), probe == null ? null : probe.latencyMs(),
                finalDetail, LocalDateTime.now());
    }

    /** 仅做 TCP 连通性探测，不发送任何凭证；2 秒超时。 */
    private Probe probe(String urlOrHost) {
        if (!hasText(urlOrHost)) {
            return new Probe(false, 0, "未配置地址");
        }
        long start = System.currentTimeMillis();
        String value = urlOrHost.trim();
        try {
            URI uri;
            if (value.contains("://")) {
                uri = URI.create(value);
            } else {
                uri = URI.create("https://" + value);
            }
            String host = uri.getHost();
            if (!hasText(host)) {
                return new Probe(false, System.currentTimeMillis() - start, "地址无法解析");
            }
            int port = uri.getPort() > 0 ? uri.getPort()
                    : ("http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 2000);
                return new Probe(true, System.currentTimeMillis() - start, host + ":" + port);
            }
        } catch (Exception exception) {
            long latency = System.currentTimeMillis() - start;
            String message = exception.getClass().getSimpleName();
            if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
                message += " " + exception.getMessage();
            }
            if (message.length() > 120) {
                message = message.substring(0, 120);
            }
            return new Probe(false, latency, message);
        }
    }

    private long countEnabledFeedSources() {
        try {
            return officialFeedSourceMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query
                    .QueryWrapper<OfficialFeedSourceEntity>().eq("enabled", true));
        } catch (Exception exception) {
            return 0;
        }
    }

    private String firstEnabledFeedUrl() {
        try {
            List<OfficialFeedSourceEntity> list = officialFeedSourceMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OfficialFeedSourceEntity>()
                            .eq("enabled", true).orderByAsc("id").last("LIMIT 1"));
            return list.isEmpty() ? null : list.get(0).getListUrl();
        } catch (Exception exception) {
            return null;
        }
    }

    private AdminSystemHealthResponse.Component checkDisk() {
        long start = System.currentTimeMillis();
        try {
            FileStore store = Files.getFileStore(Path.of(".").toAbsolutePath());
            long usable = store.getUsableSpace();
            long total = store.getTotalSpace();
            long elapsed = System.currentTimeMillis() - start;
            String detail = String.format(Locale.ROOT, "可用 %.1f GB / 共 %.1f GB",
                    usable / 1073741824.0, total / 1073741824.0);
            return new AdminSystemHealthResponse.Component("磁盘", usable < 512L * 1024 * 1024 ? "DOWN" : "UP",
                    detail, elapsed);
        } catch (Exception exception) {
            return new AdminSystemHealthResponse.Component("磁盘", "DOWN", exception.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    private AdminSystemHealthResponse.Component checkScheduledTasks() {
        long start = System.currentTimeMillis();
        try {
            int count = scheduledTaskRegistry.taskCount();
            return new AdminSystemHealthResponse.Component("定时任务", count > 0 ? "UP" : "NOT_CONFIGURED",
                    count > 0 ? "已注册 " + count + " 个 @Scheduled 任务" : "未发现已注册的定时任务",
                    System.currentTimeMillis() - start);
        } catch (Exception exception) {
            return new AdminSystemHealthResponse.Component("定时任务", "DOWN", exception.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    private AdminSystemHealthResponse.Component checkAi() {
        long start = System.currentTimeMillis();
        boolean enabled = Boolean.parseBoolean(environment.getProperty("studenthub.ai.enabled", "false"));
        boolean key = hasText(environment.getProperty("studenthub.ai.api-key"));
        String model = environment.getProperty("studenthub.ai.model", "");
        String status = !enabled ? "NOT_CONFIGURED" : (key ? "UP" : "NOT_CONFIGURED");
        String detail = !enabled ? "AI_ENABLED=false" : (key ? "模型 " + model + " 已配置（连通性请在外部集成测试）"
                : "缺少 AI_API_KEY");
        return new AdminSystemHealthResponse.Component("AI 供应商", status, detail,
                System.currentTimeMillis() - start);
    }

    private AdminSystemHealthResponse.Component checkJw() {
        long start = System.currentTimeMillis();
        String base = environment.getProperty("studenthub.jw.jw-base-url", "");
        return new AdminSystemHealthResponse.Component("教务系统",
                hasText(base) ? "UP" : "NOT_CONFIGURED",
                hasText(base) ? "教务地址已配置（连通性请在外部集成测试）" : "缺少 JW_JW_BASE_URL",
                System.currentTimeMillis() - start);
    }

    private AdminSystemHealthResponse.Component checkOfficialFeed() {
        long start = System.currentTimeMillis();
        long sources = countEnabledFeedSources();
        return new AdminSystemHealthResponse.Component("官网抓取", sources > 0 ? "UP" : "NOT_CONFIGURED",
                sources > 0 ? sources + " 个启用抓取源" : "未配置启用的官网抓取源",
                System.currentTimeMillis() - start);
    }

    private AdminSystemHealthResponse.Component checkVersion() {
        long start = System.currentTimeMillis();
        try {
            List<AppVersionEntity> latest = appVersionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AppVersionEntity>()
                            .eq("status", "PUBLISHED").orderByDesc("published_at").last("LIMIT 1"));
            if (latest.isEmpty()) {
                return new AdminSystemHealthResponse.Component("版本信息", "NOT_CONFIGURED",
                        "暂无已发布版本", System.currentTimeMillis() - start);
            }
            AppVersionEntity version = latest.get(0);
            return new AdminSystemHealthResponse.Component("版本信息", "UP",
                    version.getPlatform() + " v" + version.getVersionName() + "（已发布）",
                    System.currentTimeMillis() - start);
        } catch (Exception exception) {
            return new AdminSystemHealthResponse.Component("版本信息", "DOWN", exception.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    @Override
    public String getValue(String key, Long campusId) {
        if (!StringUtils.hasText(key)) return null;
        SysConfigEntity entity = configMapper.selectOne(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getConfigKey, key)
                .in(SysConfigEntity::getCampusId, campusId == null ? List.of(0L) : List.of(0L, campusId))
                .orderByDesc(SysConfigEntity::getCampusId)
                .last("LIMIT 1"));
        return entity == null ? null : entity.getConfigValue();
    }

    @Override
    public boolean getBoolean(String key, Long campusId, boolean fallback) {
        String value = getValue(key, campusId);
        if (value == null) return fallback;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private AdminSystemHealthResponse.Component checkDb() {
        long start = System.currentTimeMillis();
        try {
            JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
            if (jdbc == null) return new AdminSystemHealthResponse.Component("MySQL", "NOT_CONFIGURED", "未注入 JdbcTemplate", null);
            jdbc.queryForObject("SELECT 1", Integer.class);
            return new AdminSystemHealthResponse.Component("MySQL", "UP", "主库连接正常", System.currentTimeMillis() - start);
        } catch (Exception exception) {
            return new AdminSystemHealthResponse.Component("MySQL", "DOWN", exception.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    private AdminSystemHealthResponse.Component checkRedis() {
        long start = System.currentTimeMillis();
        try {
            var redis = redisProvider.getIfAvailable();
            if (redis == null) return new AdminSystemHealthResponse.Component("Redis", "NOT_CONFIGURED", "未注入 Redis 客户端", null);
            var factory = redis.getConnectionFactory();
            if (factory == null) return new AdminSystemHealthResponse.Component("Redis", "NOT_CONFIGURED", "连接工厂为空", null);
            try (var connection = factory.getConnection()) {
                connection.ping();
            }
            return new AdminSystemHealthResponse.Component("Redis", "UP", "会话存储可用", System.currentTimeMillis() - start);
        } catch (Exception exception) {
            return new AdminSystemHealthResponse.Component("Redis", "DOWN", exception.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    private AdminSystemHealthResponse.Component checkStorage() {
        long start = System.currentTimeMillis();
        try {
            String storage = fileProperties.normalizedStorage();
            if (fileProperties.objectStorage()) {
                boolean configured = storageConfigured();
                return new AdminSystemHealthResponse.Component("文件存储", configured ? "UP" : "NOT_CONFIGURED",
                        storageDetail(), System.currentTimeMillis() - start);
            }
            Path path = Path.of(fileProperties.getStorageDir());
            Files.createDirectories(path);
            boolean writable = Files.isWritable(path);
            return new AdminSystemHealthResponse.Component("文件存储", writable ? "UP" : "DOWN",
                    "本地目录 " + path.toAbsolutePath(), System.currentTimeMillis() - start);
        } catch (Exception exception) {
            return new AdminSystemHealthResponse.Component("文件存储", "DOWN", exception.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    private Map<String, Object> snapshot(SysConfigEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", entity.getConfigKey());
        map.put("campusId", entity.getCampusId());
        map.put("value", Boolean.TRUE.equals(entity.getIsSecret())
                ? (StringUtils.hasText(entity.getConfigValue()) ? "******" : "")
                : entity.getConfigValue());
        map.put("type", entity.getValueType());
        return map;
    }

    private void validateValue(String valueType, String value, String key) {
        String type = valueType == null ? "STRING" : valueType.toUpperCase();
        if (!VALUE_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不支持的配置类型: " + type);
        }
        if ("BOOLEAN".equals(type) && !("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "配置 " + key + " 必须是 true/false");
        }
        if ("INT".equals(type)) {
            try { Integer.parseInt(value); } catch (Exception e) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "配置 " + key + " 必须是整数");
            }
        }
        if ("LONG".equals(type)) {
            try { Long.parseLong(value); } catch (Exception e) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "配置 " + key + " 必须是整数");
            }
        }
    }

    private boolean storageConfigured() {
        String storage = fileProperties.normalizedStorage();
        return switch (storage) {
            case "local" -> hasText(fileProperties.getStorageDir());
            case "minio" -> hasText(fileProperties.getMinioEndpoint())
                    && hasText(fileProperties.getMinioBucket())
                    && hasText(fileProperties.getMinioAccessKey())
                    && hasText(fileProperties.getMinioSecretKey());
            case "oss" -> hasText(fileProperties.getOssEndpoint())
                    && hasText(fileProperties.getOssBucket())
                    && hasText(fileProperties.getOssAccessKeyId())
                    && hasText(fileProperties.getOssAccessKeySecret());
            default -> false;
        };
    }

    private String storageDetail() {
        return switch (fileProperties.normalizedStorage()) {
            case "minio" -> "MinIO " + fileProperties.getMinioEndpoint() + "/" + fileProperties.getMinioBucket();
            case "oss" -> "阿里云 OSS " + fileProperties.getOssEndpoint() + "/" + fileProperties.getOssBucket();
            default -> "本地目录 " + fileProperties.getStorageDir();
        };
    }

    private String storageMissingDetail() {
        return switch (fileProperties.normalizedStorage()) {
            case "minio" -> "MinIO Endpoint/桶/AccessKey/SecretKey 未配置完整";
            case "oss" -> "OSS Endpoint/桶/AccessKeyId/AccessKeySecret 未配置完整";
            default -> "本地存储目录未配置";
        };
    }

    private void validateIntegrationConfigValue(String key, String value) {
        if (!AuthIntegrationConfigKeys.isManagedKey(key)) {
            return;
        }
        if (AuthIntegrationConfigKeys.JW_ENCRYPT_KEY.equals(key) && !StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "教务会话加密密钥不能为空");
        }
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (AuthIntegrationConfigKeys.FILE_STORAGE.equals(key)
                && !Set.of("local", "minio", "oss").contains(value.trim().toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "存储实现只能是 local、minio 或 oss");
        }
        if ((AuthIntegrationConfigKeys.FILE_MINIO_ENDPOINT.equals(key)
                || AuthIntegrationConfigKeys.FILE_MINIO_PUBLIC_ENDPOINT.equals(key))) {
            requireHttpUrl(key, value);
        }
        if ((AuthIntegrationConfigKeys.FILE_OSS_ENDPOINT.equals(key)
                || AuthIntegrationConfigKeys.FILE_OSS_PUBLIC_ENDPOINT.equals(key))
                && !isHttpOrHost(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "配置 " + key + " 必须是 http/https 地址或合法域名");
        }
        if ((AuthIntegrationConfigKeys.FILE_MINIO_BUCKET.equals(key)
                || AuthIntegrationConfigKeys.FILE_OSS_BUCKET.equals(key))
                && !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{1,62}")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "桶名称只能包含字母、数字、点、下划线和短横线（3-63 位）");
        }
        if (AuthIntegrationConfigKeys.FILE_MAX_SIZE_MB.equals(key)) {
            int size = Integer.parseInt(value);
            if (size < 1 || size > 1024) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "单文件大小上限必须在 1-1024 MB 之间");
            }
        }
        if (AuthIntegrationConfigKeys.FILE_VIEW_TTL_MINUTES.equals(key)) {
            int ttl = Integer.parseInt(value);
            if (ttl < 1 || ttl > 1440) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "签名有效期必须在 1-1440 分钟之间");
            }
        }
        if (AuthIntegrationConfigKeys.QQ_MODE.equals(key) || AuthIntegrationConfigKeys.WECHAT_MODE.equals(key)) {
            if (!"real".equalsIgnoreCase(value) && !"mock".equalsIgnoreCase(value)) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                        "配置 " + key + " 只能是 real 或 mock");
            }
        }
        if (AuthIntegrationConfigKeys.QQ_API_BASE_URL.equals(key)
                || AuthIntegrationConfigKeys.WECHAT_API_BASE_URL.equals(key)) {
            try {
                URI uri = URI.create(value);
                if (uri.getHost() == null
                        || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
                    throw new IllegalArgumentException();
                }
            } catch (Exception exception) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                        "配置 " + key + " 必须是 http/https 地址");
            }
        }
        if (AuthIntegrationConfigKeys.MAIL_PORT.equals(key)) {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "SMTP 端口必须在 1-65535 之间");
            }
        }
        if (AuthIntegrationConfigKeys.JW_SSO_BASE_URL.equals(key)
                || AuthIntegrationConfigKeys.JW_JW_BASE_URL.equals(key)
                || AuthIntegrationConfigKeys.JW_CAS_SERVICE.equals(key)
                || AuthIntegrationConfigKeys.AMAP_BASE_URL.equals(key)) {
            requireHttpUrl(key, value);
        }
        if (AuthIntegrationConfigKeys.JW_RSA_MODULUS.equals(key)
                && !value.matches("[0-9a-fA-F]+")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "教务 RSA 模数必须是十六进制字符串");
        }
        if (AuthIntegrationConfigKeys.JW_RSA_EXPONENT.equals(key)
                && !value.matches("[0-9a-fA-F]+")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "教务 RSA 指数必须是十六进制字符串");
        }
        if (AuthIntegrationConfigKeys.JW_ENCRYPT_KEY.equals(key) && value.length() < 16) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "教务会话加密密钥至少 16 个字符");
        }
        if (AuthIntegrationConfigKeys.RAG_JDBC_URL.equals(key)
                && !value.startsWith("jdbc:postgresql://")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "RAG JDBC 地址必须是 jdbc:postgresql:// 开头");
        }
        if (AuthIntegrationConfigKeys.RAG_LEXICAL_MIN_SIMILARITY.equals(key)) {
            double similarity;
            try {
                similarity = Double.parseDouble(value);
            } catch (NumberFormatException exception) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "词法最小相似度必须是 0-1 的小数");
            }
            if (similarity < 0 || similarity > 1) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "词法最小相似度必须在 0-1 之间");
            }
        }
        if (AuthIntegrationConfigKeys.JW_SESSION_TTL_HOURS.equals(key)
                && (Integer.parseInt(value) < 1 || Integer.parseInt(value) > 720)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "教务会话保活时长必须在 1-720 小时之间");
        }
        if ((AuthIntegrationConfigKeys.JW_CONNECT_TIMEOUT_SECONDS.equals(key)
                || AuthIntegrationConfigKeys.JW_READ_TIMEOUT_SECONDS.equals(key)
                || AuthIntegrationConfigKeys.AMAP_CONNECT_TIMEOUT_SECONDS.equals(key)
                || AuthIntegrationConfigKeys.AMAP_READ_TIMEOUT_SECONDS.equals(key))
                && (Integer.parseInt(value) < 1 || Integer.parseInt(value) > 300)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "超时时间必须在 1-300 秒之间");
        }
        if (AuthIntegrationConfigKeys.JW_AUTH_RATE_LIMIT_PER_MINUTE.equals(key)
                && (Integer.parseInt(value) < 1 || Integer.parseInt(value) > 1000)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "认证限流必须在 1-1000 次/分钟之间");
        }
        if (AuthIntegrationConfigKeys.RAG_EMBEDDING_BATCH_SIZE.equals(key)
                && (Integer.parseInt(value) < 1 || Integer.parseInt(value) > 256)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "嵌入批大小必须在 1-256 之间");
        }
        if ((AuthIntegrationConfigKeys.RAG_LEXICAL_CANDIDATE_LIMIT.equals(key)
                || AuthIntegrationConfigKeys.RAG_VECTOR_CANDIDATE_LIMIT.equals(key))
                && (Integer.parseInt(value) < 1 || Integer.parseInt(value) > 500)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "召回候选数必须在 1-500 之间");
        }
        if (AuthIntegrationConfigKeys.RAG_RRF_K.equals(key)
                && (Integer.parseInt(value) < 1 || Integer.parseInt(value) > 1000)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "RRF 常数必须在 1-1000 之间");
        }
        if (AuthIntegrationConfigKeys.RAG_MAX_DOCUMENT_SIZE_MB.equals(key)
                && (Integer.parseInt(value) < 1 || Integer.parseInt(value) > 1024)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文档大小上限必须在 1-1024 MB 之间");
        }
        if (AuthIntegrationConfigKeys.RAG_MAX_PARSED_CHARACTERS.equals(key)
                && Integer.parseInt(value) < 1000) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "解析字符上限不能小于 1000");
        }
    }

    private boolean isHttpOrHost(String value) {
        try {
            String candidate = value.contains("://") ? value : "https://" + value;
            URI uri = URI.create(candidate);
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (Exception exception) {
            return false;
        }
    }

    private void requireHttpUrl(String key, String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null
                    || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "配置 " + key + " 必须是 http/https 地址");
        }
    }

    private String authValue(Map<String, AuthIntegrationRuntimeConfig.EffectiveConfig> auth,
                             String key, String fallback) {
        AuthIntegrationRuntimeConfig.EffectiveConfig config = auth.get(key);
        if (config == null || config.value() == null
                || AuthIntegrationRuntimeConfig.SOURCE_UNSET.equals(config.source())
                || AuthIntegrationRuntimeConfig.SOURCE_ERROR.equals(config.source())) {
            return fallback;
        }
        return config.value();
    }

    private int authInt(Map<String, AuthIntegrationRuntimeConfig.EffectiveConfig> auth,
                        String key, int fallback) {
        try {
            String value = authValue(auth, key, null);
            return hasText(value) ? Integer.parseInt(value.trim()) : fallback;
        } catch (Exception exception) {
            return fallback;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
    /** 连通性探测结果（不含任何凭证）。 */
    private record Probe(boolean reachable, long latencyMs, String detail) {
    }
}
