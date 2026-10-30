package com.studenthub.modules.appversion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.storage.BinaryStorage;
import com.studenthub.modules.appversion.controller.dto.AdminAppVersionRequest;
import com.studenthub.modules.appversion.controller.dto.AppVersionCheckResponse;
import com.studenthub.modules.appversion.controller.dto.AppVersionResponse;
import com.studenthub.modules.appversion.entity.AppVersionEntity;
import com.studenthub.modules.appversion.mapper.AppVersionMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * App 安装包版本管理（方案：管理端发布 → 公开接口检查更新）。
 * <p>安装包上传/下载均走流式（{@link BinaryStorage#putStream}），不经过 multipart，也不整包读入内存；
 * 同一平台+渠道同时只保留一个 PUBLISHED，发布新版本时旧版本自动下线。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppVersionService {

    public static final Set<String> PLATFORMS = Set.of("ANDROID", "IOS", "HARMONY");
    public static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "OFFLINE");
    private static final Set<String> PACKAGE_EXTENSIONS = Set.of("apk", "ipa", "hap");
    private static final long MAX_PACKAGE_BYTES = 300L * 1024L * 1024L;
    private static final String PACKAGE_KEY_PREFIX = "static/packages/";
    private static final String PUBLIC_PACKAGE_PATH = "/api/public/app-version/";
    private static final String DEFAULT_CHANNEL = "OFFICIAL";

    private final AppVersionMapper versionMapper;
    private final BinaryStorage binaryStorage;

    public List<AppVersionResponse> list(String platform, String status) {
        String normalizedPlatform = StringUtils.hasText(platform) ? platform.trim().toUpperCase(Locale.ROOT) : null;
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : null;
        List<AppVersionEntity> versions = versionMapper.selectList(new LambdaQueryWrapper<AppVersionEntity>()
                .eq(normalizedPlatform != null, AppVersionEntity::getPlatform, normalizedPlatform)
                .eq(normalizedStatus != null, AppVersionEntity::getStatus, normalizedStatus)
                .orderByDesc(AppVersionEntity::getVersionCode)
                .orderByDesc(AppVersionEntity::getId));
        return versions.stream().map(this::toResponse).toList();
    }

    @Transactional
    public AppVersionResponse create(AdminAppVersionRequest request) {
        AppVersionEntity entity = new AppVersionEntity();
        apply(entity, request);
        entity.setStatus("DRAFT");
        entity.setDownloadCount(0L);
        try {
            requireUpdated(versionMapper.insert(entity));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "同一平台+渠道下版本号已存在");
        }
        return toResponse(versionMapper.selectById(entity.getId()));
    }

    @Transactional
    public AppVersionResponse update(Long id, AdminAppVersionRequest request) {
        AppVersionEntity entity = requireLockedVersion(id);
        requireDraft(entity);
        AppVersionEntity update = new AppVersionEntity();
        update.setId(id);
        apply(update, request);
        update.setVersion(entity.getVersion());
        try {
            requireUpdated(versionMapper.updateById(update));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "同一平台+渠道下版本号已存在");
        }
        return toResponse(versionMapper.selectById(id));
    }

    /**
     * 原始流上传安装包（不经过 multipart）：校验扩展名/大小/ZIP 魔数，流式计算 SHA-256。
     */
    @Transactional
    public AppVersionResponse uploadPackage(Long id, String fileName, InputStream input, long contentLength) {
        AppVersionEntity entity = requireLockedVersion(id);
        requireDraft(entity);
        String safeName = sanitizeFileName(fileName);
        String extension = extensionOf(safeName);
        if (!PACKAGE_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "安装包仅支持 .apk/.ipa/.hap");
        }
        if (contentLength <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "无法确定安装包大小，请使用带 Content-Length 的上传");
        }
        if (contentLength > MAX_PACKAGE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "安装包不能超过 300MB");
        }
        Path staged = null;
        try {
            // 先落临时文件并验证实际字节数，摘要确定后再写不可变对象键；内存仅保留 64KiB。
            staged = Files.createTempFile("studenthub-release-", ".upload");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long actual = 0;
            try (InputStream stream = input; OutputStream out = Files.newOutputStream(staged)) {
                byte[] magic = stream.readNBytes(4);
                if (!isZipMagic(magic)) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "安装包内容不是有效的 ZIP/APK/IPA/HAP 格式");
                }
                out.write(magic);
                digest.update(magic);
                actual += magic.length;
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    actual += read;
                    if (actual > contentLength || actual > MAX_PACKAGE_BYTES) {
                        throw new BusinessException(ErrorCode.INVALID_PARAMETER, "安装包实际大小与声明不符");
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            if (actual != contentLength) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "安装包实际大小与声明不符");
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            String objectKey = PACKAGE_KEY_PREFIX + entity.getPlatform().toLowerCase(Locale.ROOT) + "/"
                    + sanitizeForObjectKey(entity.getChannel()) + "/" + id + "/" + sha256 + "." + extension;
            try (InputStream stream = Files.newInputStream(staged)) {
                binaryStorage.putStream(objectKey, stream, actual, contentTypeOf(extension));
            }
            AppVersionEntity update = new AppVersionEntity();
            update.setId(id);
            update.setPackageKey(objectKey);
            update.setPackageFileName(safeName);
            update.setPackageSize(actual);
            update.setPackageSha256(sha256);
            update.setVersion(entity.getVersion());
            requireUpdated(versionMapper.updateById(update));
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "安装包读取失败，请重试");
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "服务端摘要算法不可用");
        } catch (RuntimeException exception) {
            log.error("安装包存储失败, versionId={}, fileName={}", id, safeName, exception);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "安装包保存失败，请稍后重试");
        } finally {
            if (staged != null) {
                try { Files.deleteIfExists(staged); }
                catch (IOException exception) { log.warn("临时安装包清理失败, versionId={}", id); }
            }
        }
        return toResponse(versionMapper.selectById(id));
    }

    @Transactional
    public AppVersionResponse publish(Long id) {
        AppVersionEntity initial = requireVersion(id);
        // 每个渠道一行数据库锁，跨实例发布也必须串行；事务失败会同时回滚旧版下线和新版发布。
        versionMapper.ensureReleaseChannel(initial.getPlatform(), initial.getChannel());
        versionMapper.lockReleaseChannel(initial.getPlatform(), initial.getChannel());
        AppVersionEntity entity = requireLockedVersion(id);
        if (!initial.getPlatform().equals(entity.getPlatform()) || !initial.getChannel().equals(entity.getChannel())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "版本渠道已变更，请刷新后重试");
        }
        if ("PUBLISHED".equals(entity.getStatus())) return toResponse(entity);
        boolean hasPackage = StringUtils.hasText(entity.getPackageKey());
        boolean hasExternalUrl = StringUtils.hasText(entity.getDownloadUrl());
        if (!hasPackage && !hasExternalUrl) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "发布前必须先上传安装包或填写外部下载地址");
        }
        if (hasExternalUrl && !isHttpUrl(entity.getDownloadUrl())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "外部下载地址必须以 http(s):// 开头");
        }
        // 同一 platform+channel 只保留一个已发布版本
        versionMapper.update(null, new LambdaUpdateWrapper<AppVersionEntity>()
                .eq(AppVersionEntity::getPlatform, entity.getPlatform())
                .eq(AppVersionEntity::getChannel, entity.getChannel())
                .eq(AppVersionEntity::getStatus, "PUBLISHED")
                .ne(AppVersionEntity::getId, id)
                .set(AppVersionEntity::getStatus, "OFFLINE"));
        AppVersionEntity update = new AppVersionEntity();
        update.setId(id);
        update.setStatus("PUBLISHED");
        update.setPublishedAt(LocalDateTime.now());
        update.setVersion(entity.getVersion());
        requireUpdated(versionMapper.updateById(update));
        return toResponse(versionMapper.selectById(id));
    }

    @Transactional
    public AppVersionResponse offline(Long id) {
        AppVersionEntity entity = requireLockedVersion(id);
        if (!"PUBLISHED".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "仅已发布版本可以下线");
        }
        AppVersionEntity update = new AppVersionEntity();
        update.setId(id);
        update.setStatus("OFFLINE");
        update.setVersion(entity.getVersion());
        requireUpdated(versionMapper.updateById(update));
        return toResponse(versionMapper.selectById(id));
    }

    @Transactional
    public void delete(Long id) {
        AppVersionEntity entity = requireLockedVersion(id);
        if ("PUBLISHED".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "已发布版本不可直接删除，请先下线");
        }
        requireUpdated(versionMapper.deleteById(id));
    }

    public AppVersionCheckResponse check(String platform, String channel, Integer currentVersionCode) {
        String normalizedPlatform = requirePlatform(platform);
        String normalizedChannel = normalizeChannel(channel);
        int current = currentVersionCode == null || currentVersionCode < 0 ? 0 : currentVersionCode;
        AppVersionEntity latest = findLatestPublished(normalizedPlatform, normalizedChannel);
        if (latest == null) {
            return new AppVersionCheckResponse(false, false, current, null);
        }
        boolean hasUpdate = latest.getVersionCode() != null && latest.getVersionCode() > current;
        int minSupported = latest.getMinSupportedVersionCode() == null ? 0 : latest.getMinSupportedVersionCode();
        boolean forceUpdate = Boolean.TRUE.equals(latest.getForceUpdate()) || current < minSupported;
        return new AppVersionCheckResponse(hasUpdate, forceUpdate, current, toLatest(latest));
    }

    /** 稳定公开入口：每次请求解析当前已发布版，不绑定历史版本 ID。 */
    public void downloadLatest(String platform, String channel, HttpServletResponse response) {
        AppVersionEntity latest = findLatestPublished(requirePlatform(platform), normalizeChannel(channel));
        if (latest == null) throw new BusinessException(ErrorCode.NOT_FOUND, "当前渠道尚无发布版本");
        response.setHeader("Cache-Control", "no-store");
        if (StringUtils.hasText(latest.getPackageKey())) {
            streamPackage(latest, response, true);
        } else if (isHttpUrl(latest.getDownloadUrl())) {
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader("Location", latest.getDownloadUrl());
        } else throw new BusinessException(ErrorCode.NOT_FOUND, "当前版本尚无下载地址");
    }

    /** 管理端下载（任意状态，用于校验包） */
    public void downloadForAdmin(Long id, HttpServletResponse response) {
        streamPackage(requireVersion(id), response, false);
    }

    /** 公开下载（仅已发布版本，并累加下载量） */
    public void downloadPublic(Long id, HttpServletResponse response) {
        AppVersionEntity entity = requireVersion(id);
        if (!"PUBLISHED".equals(entity.getStatus()) || !StringUtils.hasText(entity.getPackageKey())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "安装包不存在");
        }
        streamPackage(entity, response, true);
    }

    private void streamPackage(AppVersionEntity entity, HttpServletResponse response, boolean countDownload) {
        if (!StringUtils.hasText(entity.getPackageKey())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该版本未上传安装包");
        }
        String extension = extensionOf(entity.getPackageFileName());
        long size = entity.getPackageSize() == null ? -1 : entity.getPackageSize();
        response.setHeader("X-App-Version-Id", String.valueOf(entity.getId()));
        if (StringUtils.hasText(entity.getPackageSha256())) {
            response.setHeader("ETag", "\"" + entity.getPackageSha256() + "\"");
        }
        response.setContentType(contentTypeOf(extension));
        if (size > 0) {
            response.setContentLengthLong(size);
        }
        response.setHeader("Content-Disposition", contentDisposition(entity.getPackageFileName()));
        try (InputStream stream = binaryStorage.get(entity.getPackageKey())) {
            StreamUtils.copy(stream, response.getOutputStream());
            response.flushBuffer();
        } catch (Exception exception) {
            log.error("安装包下载失败, versionId={}, key={}", entity.getId(), entity.getPackageKey(), exception);
            throw new BusinessException(ErrorCode.NOT_FOUND, "安装包读取失败");
        }
        if (countDownload) {
            try {
                versionMapper.incrementDownloadCount(entity.getId());
            } catch (Exception exception) {
                log.warn("安装包下载计数失败, versionId={}", entity.getId());
            }
        }
    }

    private AppVersionEntity findLatestPublished(String platform, String channel) {
        return versionMapper.selectOne(new LambdaQueryWrapper<AppVersionEntity>()
                .eq(AppVersionEntity::getPlatform, platform)
                .eq(AppVersionEntity::getChannel, channel)
                .eq(AppVersionEntity::getStatus, "PUBLISHED")
                .orderByDesc(AppVersionEntity::getVersionCode)
                .last("LIMIT 1"));
    }

    private void apply(AppVersionEntity entity, AdminAppVersionRequest request) {
        entity.setPlatform(requirePlatform(request.platform()));
        entity.setChannel(normalizeChannel(request.channel()));
        entity.setVersionCode(request.versionCode());
        entity.setVersionName(request.versionName().trim());
        entity.setMinSupportedVersionCode(
                request.minSupportedVersionCode() == null ? 0 : Math.max(0, request.minSupportedVersionCode()));
        entity.setForceUpdate(request.forceUpdate() != null && request.forceUpdate());
        entity.setUpdateLog(request.updateLog() == null ? null : request.updateLog().trim());
        String url = request.downloadUrl() == null ? null : request.downloadUrl().trim();
        if (StringUtils.hasText(url) && !isHttpUrl(url)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "外部下载地址必须以 http(s):// 开头");
        }
        entity.setDownloadUrl(StringUtils.hasText(url) ? url : null);
    }

    private void requireUpdated(int rows) {
        if (rows != 1) throw new BusinessException(ErrorCode.INVALID_PARAMETER, "版本已被其他操作更新，请刷新后重试");
    }

    private AppVersionEntity requireLockedVersion(Long id) {
        AppVersionEntity entity = versionMapper.selectOne(new LambdaQueryWrapper<AppVersionEntity>()
                .eq(AppVersionEntity::getId, id).last("FOR UPDATE"));
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "版本不存在");
        return entity;
    }

    private AppVersionEntity requireVersion(Long id) {
        AppVersionEntity entity = versionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "版本不存在");
        }
        return entity;
    }

    private void requireDraft(AppVersionEntity entity) {
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "仅草稿状态的版本可以修改或上传安装包");
        }
    }

    private String requirePlatform(String platform) {
        String normalized = platform == null ? "" : platform.trim().toUpperCase(Locale.ROOT);
        if (!PLATFORMS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "平台仅支持 ANDROID/IOS/HARMONY");
        }
        return normalized;
    }

    private String normalizeChannel(String channel) {
        String value = StringUtils.hasText(channel) ? channel.trim().toUpperCase(Locale.ROOT) : DEFAULT_CHANNEL;
        if (!value.matches("[A-Z0-9_-]{1,32}")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "渠道仅支持 1–32 位字母、数字、下划线和连字符");
        }
        return value;
    }

    private boolean isZipMagic(byte[] magic) {
        if (magic == null || magic.length < 4) {
            return false;
        }
        // PK\x03\x04（普通 ZIP，APK/IPA/HAP 均为 ZIP 容器）或 PK\x05\x06（空包）
        return magic[0] == 'P' && magic[1] == 'K'
                && ((magic[2] == 3 && magic[3] == 4) || (magic[2] == 5 && magic[3] == 6));
    }

    private String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "fileName 不能为空");
        }
        String normalized = fileName.trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (!StringUtils.hasText(base) || base.length() > 128) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "安装包文件名不合法");
        }
        return base;
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizeForObjectKey(String versionName) {
        String safe = versionName == null ? "app" : versionName.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "app" : safe;
    }

    private boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String contentTypeOf(String extension) {
        return "apk".equals(extension) ? "application/vnd.android.package-archive" : "application/octet-stream";
    }

    private String contentDisposition(String fileName) {
        String name = StringUtils.hasText(fileName) ? fileName : "app-package";
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded;
    }

    /**
     * 解析安装包下载地址：对象存储（OSS/MinIO）生效时返回存储直链（短时签名），
     * 客户端直接从存储/CDN 下载，不占用应用带宽；本地存储返回 null，由调用方
     * 回退到应用内流式下载接口。
     */
    private String resolveDownloadUrl(AppVersionEntity entity) {
        if (!StringUtils.hasText(entity.getPackageKey())) {
            return entity.getDownloadUrl();
        }
        // 7 天有效；参数必须全部在 OSS 签名时生成，禁止事后拼接破坏签名。
        return binaryStorage.presign(entity.getPackageKey(), 7 * 24 * 3600);
    }

    private AppVersionResponse toResponse(AppVersionEntity entity) {
        boolean hasPackage = StringUtils.hasText(entity.getPackageKey());
        String downloadUrl = hasPackage
                ? PUBLIC_PACKAGE_PATH + entity.getId() + "/package"
                : entity.getDownloadUrl();
        return new AppVersionResponse(entity.getId(), entity.getPlatform(), entity.getChannel(),
                entity.getVersionCode(), entity.getVersionName(), entity.getMinSupportedVersionCode(),
                entity.getForceUpdate(), entity.getUpdateLog(), entity.getPackageFileName(),
                entity.getPackageSize(), entity.getPackageSha256(), hasPackage, downloadUrl,
                entity.getStatus(), entity.getPublishedAt(), entity.getDownloadCount(), entity.getCreatedAt());
    }

    private AppVersionCheckResponse.Latest toLatest(AppVersionEntity entity) {
        boolean hasPackage = StringUtils.hasText(entity.getPackageKey());
        // 优先存储直链（OSS/CDN），生成失败或本地存储时回退应用内流式接口
        String downloadUrl = hasPackage ? resolveDownloadUrl(entity) : entity.getDownloadUrl();
        if (!StringUtils.hasText(downloadUrl)) {
            downloadUrl = PUBLIC_PACKAGE_PATH + entity.getId() + "/package";
        }
        return new AppVersionCheckResponse.Latest(entity.getId(), entity.getVersionCode(), entity.getVersionName(),
                entity.getMinSupportedVersionCode(), entity.getForceUpdate(), entity.getUpdateLog(),
                downloadUrl, entity.getPackageSize(), entity.getPackageSha256(), entity.getPublishedAt());
    }
}
