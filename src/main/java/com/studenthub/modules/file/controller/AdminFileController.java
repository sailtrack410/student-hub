package com.studenthub.modules.file.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.file.config.FileProperties;
import com.studenthub.modules.file.controller.dto.AdminFileResponse;
import com.studenthub.modules.file.controller.dto.AdminFileSettingsRequest;
import com.studenthub.modules.file.controller.dto.AdminFileSettingsResponse;
import com.studenthub.modules.file.controller.dto.AdminFileStorageTestResponse;
import com.studenthub.modules.file.entity.FileStorageEntity;
import com.studenthub.modules.file.mapper.FileStorageMapper;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.integration.storage.RoutingBinaryStorage;
import com.studenthub.integration.storage.StorageProbeResult;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.systemconfig.controller.dto.AdminConfigItemResponse;
import com.studenthub.modules.systemconfig.controller.dto.AdminConfigUpdateRequest;
import com.studenthub.modules.systemconfig.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 管理端文件存储：文件列表/容量统计/逻辑删除 + 存储设置（落 sys_config）。
 *
 * <p>保存后由 SystemConfigChangedEvent 触发 FileProperties 热更新；存储路由按最新
 * storage 选择 local / MinIO / OSS，无需重启。密钥字段只写不读，页面仅展示“是否已配置”。
 * 连通性测试可按 local / MinIO / OSS 分别执行，不切换当前生效存储。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/files")
@Tag(name = "管理端-文件存储", description = "文件列表、容量统计、逻辑删除与存储设置")
@RequiredArgsConstructor
public class AdminFileController {

    private static final String MIME_POLICY = "允许上传：图片 jpeg/png/webp；音频 mp3/aac/m4a/wav/ogg/opus/caf；"
            + "文档 pdf/docx/txt/md；服务端按扩展名 + MIME + 大小三重校验，非白名单类型拒绝。";
    private static final String NOTE = "设置保存到配置中心（file 分组）后立即热更新：存储实现、大小上限、签名 TTL、"
            + "本地目录、MinIO/OSS 端点与密钥均按最新值生效，无需重启。密钥字段留空表示不修改。"
            + "连通性测试可按 local / MinIO / OSS 分别执行，测试使用已保存配置且不会切换当前存储。";

    private final FileStorageMapper fileStorageMapper;
    private final FileStorageService fileStorageService;
    private final FileProperties fileProperties;
    private final RoutingBinaryStorage routingBinaryStorage;
    private final SystemConfigService systemConfigService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "文件列表", description = "按文件名/内容类型过滤，按上传时间倒序；逻辑删除的文件不返回。",
            operationId = "adminPageFiles")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('file:manage')")
    public Result<PageResult<AdminFileResponse>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String contentType) {
        int size = Math.max(1, Math.min(pageSize, 100));
        LambdaQueryWrapper<FileStorageEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(FileStorageEntity::getOriginalName, keyword.trim());
        }
        if (contentType != null && !contentType.isBlank()) {
            wrapper.likeRight(FileStorageEntity::getContentType, contentType.trim().toLowerCase(Locale.ROOT));
        }
        wrapper.orderByDesc(FileStorageEntity::getCreatedAt);
        Page<FileStorageEntity> page = fileStorageMapper.selectPage(new Page<>(Math.max(1, pageNum), size), wrapper);
        List<AdminFileResponse> records = page.getRecords().stream().map(this::toResponse).toList();
        return Result.success(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records));
    }

    @GetMapping("/settings")
    @Operation(summary = "文件存储设置与统计", operationId = "adminGetFileSettings")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('file:manage')")
    public Result<AdminFileSettingsResponse> settings() {
        return Result.success(buildSettings());
    }

    @PostMapping("/settings")
    @Operation(summary = "保存文件存储设置",
            description = "写入 sys_config（file 分组），保存后热更新 FileProperties 并在下一次存储操作切换实现；密钥留空不修改。",
            operationId = "adminUpdateFileSettings")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('file:manage')")
    public Result<AdminFileSettingsResponse> updateSettings(
            @RequestBody @Valid AdminFileSettingsRequest request) {
        List<AdminConfigUpdateRequest.Item> items = new ArrayList<>();
        if (request.maxSizeMb() != null) {
            items.add(item("file.max_size_mb", String.valueOf(request.maxSizeMb())));
        }
        if (request.viewTtlMinutes() != null) {
            items.add(item("file.view_ttl_minutes", String.valueOf(request.viewTtlMinutes())));
        }
        String storage = null;
        if (request.storage() != null && !request.storage().isBlank()) {
            storage = request.storage().trim().toLowerCase(Locale.ROOT);
            if (!"local".equals(storage) && !"minio".equals(storage) && !"oss".equals(storage)) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "存储实现仅支持 local / minio / oss");
            }
            items.add(item("file.storage", storage));
        }
        if (request.storageDir() != null) {
            items.add(item("file.storage_dir", request.storageDir().trim()));
        }
        if (hasText(request.signSecret())) {
            items.add(item("file.sign_secret", request.signSecret().trim()));
        }
        if (request.minioEndpoint() != null) {
            items.add(item("file.minio_endpoint", request.minioEndpoint().trim()));
        }
        if (request.minioPublicEndpoint() != null) {
            items.add(item("file.minio_public_endpoint", request.minioPublicEndpoint().trim()));
        }
        if (request.minioBucket() != null) {
            items.add(item("file.minio_bucket", request.minioBucket().trim()));
        }
        if (hasText(request.minioAccessKey())) {
            items.add(item("file.minio_access_key", request.minioAccessKey().trim()));
        }
        if (hasText(request.minioSecretKey())) {
            items.add(item("file.minio_secret_key", request.minioSecretKey().trim()));
        }
        if (request.ossEndpoint() != null) {
            items.add(item("file.oss_endpoint", request.ossEndpoint().trim()));
        }
        if (request.ossPublicEndpoint() != null) {
            items.add(item("file.oss_public_endpoint", request.ossPublicEndpoint().trim()));
        }
        if (request.ossBucket() != null) {
            items.add(item("file.oss_bucket", request.ossBucket().trim()));
        }
        if (hasText(request.ossAccessKeyId())) {
            items.add(item("file.oss_access_key_id", request.ossAccessKeyId().trim()));
        }
        if (hasText(request.ossAccessKeySecret())) {
            items.add(item("file.oss_access_key_secret", request.ossAccessKeySecret().trim()));
        }
        if (storage != null && !"local".equals(storage)) {
            validateObjectStorageSwitch(storage, request);
        }
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "没有要保存的设置");
        }
        AuthenticatedUser user = currentUserProvider.requireCurrentUser();
        systemConfigService.update(new AdminConfigUpdateRequest(items), user.userId(), user.displayName());
        log.info("保存文件存储设置, operatorId={}, items={}", user.userId(), items.size());
        return Result.success(buildSettings());
    }

    @PostMapping("/settings/test")
    @Operation(summary = "测试指定文件存储", description = "分别测试 local / MinIO / OSS；使用已保存配置，不改变当前生效的存储实现。",
            operationId = "adminTestFileStorage")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('file:manage')")
    public Result<AdminFileStorageTestResponse> testStorage(
            @RequestParam(required = false, defaultValue = "") String storage) {
        String target = normalizeStorageTarget(storage);
        StorageProbeResult result = routingBinaryStorage.probe(target);
        AdminFileStorageTestResponse response = new AdminFileStorageTestResponse(
                target, storageLabel(target), storageTarget(target),
                target.equals(fileProperties.normalizedStorage()),
                result.reachable(), result.latencyMs(), result.detail(), LocalDateTime.now());
        log.info("文件存储连通性测试, storage={}, reachable={}, latencyMs={}",
                target, result.reachable(), result.latencyMs());
        return Result.success(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "逻辑删除文件", description = "只标记 deleted=1，不删除对象存储中的物理文件；关联业务数据仍保留 fileId。",
            operationId = "adminDeleteFile")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('file:manage')")
    public Result<Void> delete(@PathVariable Long id) {
        FileStorageEntity entity = fileStorageMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在或已删除");
        }
        fileStorageMapper.deleteById(id);
        log.info("逻辑删除文件, fileId={}, originalName={}, operatorId={}", id, entity.getOriginalName(),
                currentUserProvider.currentUserIdOrSystem());
        return Result.success();
    }

    private String normalizeStorageTarget(String storage) {
        String target = storage == null || storage.isBlank()
                ? fileProperties.normalizedStorage()
                : storage.trim().toLowerCase(Locale.ROOT);
        if (!"local".equals(target) && !"minio".equals(target) && !"oss".equals(target)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "存储实现仅支持 local / minio / oss");
        }
        return target;
    }

    private String storageLabel(String storage) {
        return switch (storage) {
            case "minio" -> "MinIO";
            case "oss" -> "阿里云 OSS";
            default -> "本地目录";
        };
    }

    private String storageTarget(String storage) {
        return switch (storage) {
            case "minio" -> "Endpoint=" + textOrUnset(fileProperties.getMinioEndpoint())
                    + " · 桶=" + textOrUnset(fileProperties.getMinioBucket());
            case "oss" -> "Endpoint=" + textOrUnset(fileProperties.getOssEndpoint())
                    + " · 桶=" + textOrUnset(fileProperties.getOssBucket());
            default -> {
                String dir = fileProperties.getStorageDir();
                try {
                    yield Path.of(dir == null || dir.isBlank() ? "./data/files" : dir)
                            .toAbsolutePath().normalize().toString();
                } catch (Exception exception) {
                    yield textOrUnset(dir);
                }
            }
        };
    }

    private String textOrUnset(String value) {
        return value == null || value.isBlank() ? "未配置" : value.trim();
    }

    private AdminFileResponse toResponse(FileStorageEntity entity) {
        String viewPath;
        try {
            viewPath = fileStorageService.signViewPath(entity.getId());
        } catch (Exception exception) {
            viewPath = null;
        }
        return new AdminFileResponse(entity.getId(), entity.getOriginalName(), entity.getContentType(),
                entity.getFileSize(), entity.getUploaderId(), entity.getCreatedAt(), viewPath);
    }

    private AdminConfigUpdateRequest.Item item(String key, String value) {
        return new AdminConfigUpdateRequest.Item(key, value, 0L);
    }

    private void validateObjectStorageSwitch(String storage, AdminFileSettingsRequest request) {
        if ("minio".equals(storage)) {
            requireConfigured("MinIO Endpoint", firstText(request.minioEndpoint(), fileProperties.getMinioEndpoint()));
            requireConfigured("MinIO 桶", firstText(request.minioBucket(), fileProperties.getMinioBucket()));
            requireConfigured("MinIO AccessKey", firstText(request.minioAccessKey(), fileProperties.getMinioAccessKey()));
            requireConfigured("MinIO SecretKey", firstText(request.minioSecretKey(), fileProperties.getMinioSecretKey()));
        } else if ("oss".equals(storage)) {
            requireConfigured("OSS Endpoint", firstText(request.ossEndpoint(), fileProperties.getOssEndpoint()));
            requireConfigured("OSS 桶", firstText(request.ossBucket(), fileProperties.getOssBucket()));
            requireConfigured("OSS AccessKeyId", firstText(request.ossAccessKeyId(), fileProperties.getOssAccessKeyId()));
            requireConfigured("OSS AccessKeySecret",
                    firstText(request.ossAccessKeySecret(), fileProperties.getOssAccessKeySecret()));
        }
    }

    private void requireConfigured(String name, String value) {
        if (!hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, name + " 未配置，不能切换到该存储实现");
        }
    }

    private String firstText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private AdminFileSettingsResponse buildSettings() {
        long totalFiles = 0;
        long totalBytes = 0;
        List<AdminFileSettingsResponse.TypeStat> byType = new ArrayList<>();
        try {
            List<Map<String, Object>> summary = fileStorageMapper.selectMaps(new QueryWrapper<FileStorageEntity>()
                    .select("COUNT(*) AS total", "COALESCE(SUM(file_size), 0) AS bytes"));
            if (!summary.isEmpty()) {
                totalFiles = toLong(value(summary.get(0), "total"));
                totalBytes = toLong(value(summary.get(0), "bytes"));
            }
            List<Map<String, Object>> groups = fileStorageMapper.selectMaps(new QueryWrapper<FileStorageEntity>()
                    .select("content_type AS type", "COUNT(*) AS total", "COALESCE(SUM(file_size), 0) AS bytes")
                    .groupBy("content_type").orderByDesc("total"));
            for (Map<String, Object> row : groups) {
                byType.add(new AdminFileSettingsResponse.TypeStat(
                        String.valueOf(value(row, "type")), toLong(value(row, "total")), toLong(value(row, "bytes"))));
            }
        } catch (Exception exception) {
            log.warn("文件统计查询失败: {}", exception.getMessage());
        }
        long diskTotal = 0;
        long diskUsable = 0;
        try {
            FileStore store = Files.getFileStore(Path.of(".").toAbsolutePath());
            diskTotal = store.getTotalSpace();
            diskUsable = store.getUsableSpace();
        } catch (Exception ignored) {
            // 取不到磁盘信息时返回 0，页面显示“不可用”
        }
        boolean signConfigured = fileProperties.getSignSecret() != null
                && !fileProperties.getSignSecret().isBlank()
                && !fileProperties.getSignSecret().contains("dev-sign-secret");
        List<AdminConfigItemResponse> configItems = systemConfigService.list("file", 0L);
        return new AdminFileSettingsResponse(fileProperties.normalizedStorage(), fileProperties.getMaxFileSizeMb(),
                fileProperties.getViewTtlMinutes(), signConfigured, fileProperties.getStorageDir(),
                fileProperties.getMinioEndpoint(), fileProperties.getMinioPublicEndpoint(),
                fileProperties.getMinioBucket(),
                hasText(fileProperties.getMinioAccessKey()), hasText(fileProperties.getMinioSecretKey()),
                fileProperties.getOssEndpoint(), fileProperties.getOssPublicEndpoint(), fileProperties.getOssBucket(),
                hasText(fileProperties.getOssAccessKeyId()), hasText(fileProperties.getOssAccessKeySecret()),
                totalFiles, totalBytes, byType, diskTotal, diskUsable, NOTE, MIME_POLICY, configItems);
    }

    private Object value(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception exception) {
            return 0;
        }
    }
}
