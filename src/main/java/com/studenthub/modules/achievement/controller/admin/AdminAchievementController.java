package com.studenthub.modules.achievement.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.storage.BinaryStorage;
import com.studenthub.modules.achievement.controller.dto.AdminAchievementBadgeRequest;
import com.studenthub.modules.achievement.controller.dto.AdminAchievementBadgeResponse;
import com.studenthub.modules.achievement.entity.AchievementBadgeEntity;
import com.studenthub.modules.achievement.entity.AchievementCategoryEntity;
import com.studenthub.modules.achievement.mapper.AchievementBadgeMapper;
import com.studenthub.modules.achievement.mapper.AchievementCategoryMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端-成就管理：勋章/成就目录与解锁条件配置。
 * 方案B（2026-10-19）：新增码可上传服务端图标，字典接口按内容哈希自动产生新版本，客户端热更新；
 * 停用/删除勋章时同步清空用户的佩戴态，避免 /auth/me 与作者主页返回失效编码。
 */
@RestController
@RequestMapping("/api/admin/achievements")
@Tag(name = "管理端-成就管理", description = "勋章/成就发布、图标与解锁条件配置（自动发放）")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:user:manage')")
public class AdminAchievementController {

    private static final Set<String> CATEGORIES = Set.of("COMMUNITY", "SERVICE", "WORKER", "JW", "GROWTH");
    private static final Set<String> RARITIES = Set.of("COMMON", "RARE", "EPIC", "LEGENDARY");
    private static final Set<String> METRICS = Set.of("POST_COUNT", "COMMENT_COUNT", "POST_LIKES",
            "POST_LIKES_SINGLE", "REQUEST_COUNT", "REQUEST_COMPLETED", "EVALUATION_COUNT",
            "EVALUATION_FIVE_STAR", "WORKER_ORDER_COMPLETED", "JW_AUTH", "SCHEDULE_IMPORTED",
            "PROFILE_COMPLETE", "LEVEL");
    private static final long MAX_ICON_BYTES = 512L * 1024L;
    private static final String ICON_OBJECT_PREFIX = "static/assets/badges/";
    private static final String ICON_URL_PREFIX = "/api/common/assets/badges/";

    private final AchievementBadgeMapper badgeMapper;
    private final AchievementCategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final BinaryStorage binaryStorage;

    @GetMapping("/badges")
    @Operation(summary = "勋章/成就列表", description = "可按类别与启用状态过滤，按 sortOrder 升序；含分类展示名与图标地址。",
            operationId = "adminListAchievementBadges")
    public Result<List<AdminAchievementBadgeResponse>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled) {
        Map<String, String> categoryNames = categoryNames();
        List<AchievementBadgeEntity> badges = badgeMapper.selectList(
                new LambdaQueryWrapper<AchievementBadgeEntity>()
                        .eq(StringUtils.hasText(category), AchievementBadgeEntity::getCategory,
                                category == null ? null : category.trim().toUpperCase())
                        .eq(enabled != null, AchievementBadgeEntity::getEnabled, enabled)
                        .orderByAsc(AchievementBadgeEntity::getSortOrder));
        return Result.success(badges.stream().map(badge -> toResponse(badge, categoryNames)).toList());
    }

    @PostMapping("/badges")
    @Operation(summary = "创建勋章/成就", description = "badgeCode 唯一；metricCode 决定解锁指标，阈值达成后自动发放。"
            + "新增码如需在 App 展示，请随后调用图标上传接口。",
            operationId = "adminCreateAchievementBadge")
    public Result<AdminAchievementBadgeResponse> create(
            @RequestBody @Valid AdminAchievementBadgeRequest request) {
        String code = normalizeCode(request.badgeCode());
        if (code == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "勋章编码不能为空");
        }
        Long exists = badgeMapper.selectCount(new LambdaQueryWrapper<AchievementBadgeEntity>()
                .eq(AchievementBadgeEntity::getBadgeCode, code));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "勋章编码已存在");
        }
        AchievementBadgeEntity entity = new AchievementBadgeEntity();
        entity.setBadgeCode(code);
        apply(entity, request);
        badgeMapper.insert(entity);
        return Result.success(toResponse(entity, categoryNames()));
    }

    @PutMapping("/badges/{id}")
    @Operation(summary = "更新勋章/成就", description = "badgeCode 创建后不可修改；更新条件后下一轮评估生效。",
            operationId = "adminUpdateAchievementBadge")
    public Result<AdminAchievementBadgeResponse> update(
            @PathVariable Long id, @RequestBody @Valid AdminAchievementBadgeRequest request) {
        AchievementBadgeEntity entity = badgeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "勋章不存在");
        }
        if (StringUtils.hasText(request.badgeCode())
                && !normalizeCode(request.badgeCode()).equals(entity.getBadgeCode())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "勋章编码创建后不可修改");
        }
        AchievementBadgeEntity update = new AchievementBadgeEntity();
        update.setId(entity.getId());
        apply(update, request);
        update.setVersion(entity.getVersion());
        badgeMapper.updateById(update);
        return Result.success(toResponse(badgeMapper.selectById(id), categoryNames()));
    }

    @PostMapping("/badges/{id}/status")
    @Operation(summary = "启用/停用勋章", description = "停用后不再参与自动解锁评估并清理佩戴态（已获得的记录保留）。",
            operationId = "adminUpdateAchievementBadgeStatus")
    public Result<AdminAchievementBadgeResponse> status(@PathVariable Long id,
                                                        @RequestParam boolean enabled) {
        AchievementBadgeEntity entity = badgeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "勋章不存在");
        }
        AchievementBadgeEntity update = new AchievementBadgeEntity();
        update.setId(id);
        update.setEnabled(enabled);
        update.setVersion(entity.getVersion());
        badgeMapper.updateById(update);
        if (!enabled) {
            userMapper.clearWornBadgeByCode(entity.getBadgeCode());
        }
        return Result.success(toResponse(badgeMapper.selectById(id), categoryNames()));
    }

    @DeleteMapping("/badges/{id}")
    @Operation(summary = "删除勋章/成就", description = "逻辑删除，不再展示与评估，并清理佩戴态。",
            operationId = "adminDeleteAchievementBadge")
    public Result<Void> delete(@PathVariable Long id) {
        AchievementBadgeEntity entity = badgeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "勋章不存在");
        }
        badgeMapper.deleteById(id);
        userMapper.clearWornBadgeByCode(entity.getBadgeCode());
        return Result.success();
    }

    @PostMapping(value = "/badges/{id}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传/替换勋章图标",
            description = "支持 PNG/JPEG/WebP，≤512KB，服务端校验魔数；写入对象存储 static/assets/badges/{code}.{ext} "
                    + "并更新字典 iconUrl。内置 19 枚可不上传（客户端走本地素材）。",
            operationId = "adminUploadAchievementBadgeIcon")
    public Result<AdminAchievementBadgeResponse> uploadIcon(@PathVariable Long id,
                                                            @RequestPart("file") MultipartFile file) {
        AchievementBadgeEntity entity = badgeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "勋章不存在");
        }
        IconPayload icon = validateIcon(file);
        String objectKey = ICON_OBJECT_PREFIX + entity.getBadgeCode() + "." + icon.extension();
        try {
            binaryStorage.put(objectKey, icon.content(), icon.contentType());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "勋章图标保存失败，请稍后重试");
        }
        AchievementBadgeEntity update = new AchievementBadgeEntity();
        update.setId(id);
        update.setIconUrl(ICON_URL_PREFIX + entity.getBadgeCode() + "." + icon.extension());
        update.setVersion(entity.getVersion());
        badgeMapper.updateById(update);
        return Result.success(toResponse(badgeMapper.selectById(id), categoryNames()));
    }

    @DeleteMapping("/badges/{id}/icon")
    @Operation(summary = "清除勋章图标",
            description = "清除 icon_url 后字典 iconUrl=null：内置码回退客户端本地素材，新增码显示占位图。",
            operationId = "adminClearAchievementBadgeIcon")
    public Result<AdminAchievementBadgeResponse> clearIcon(@PathVariable Long id) {
        AchievementBadgeEntity entity = badgeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "勋章不存在");
        }
        badgeMapper.update(null, new LambdaUpdateWrapper<AchievementBadgeEntity>()
                .eq(AchievementBadgeEntity::getId, id)
                .set(AchievementBadgeEntity::getIconUrl, null));
        return Result.success(toResponse(badgeMapper.selectById(id), categoryNames()));
    }

    private void apply(AchievementBadgeEntity entity, AdminAchievementBadgeRequest request) {
        String category = request.category().trim().toUpperCase();
        String rarity = request.rarity().trim().toUpperCase();
        String metric = request.metricCode().trim().toUpperCase();
        if (!CATEGORIES.contains(category)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "类别不合法");
        }
        if (!RARITIES.contains(rarity)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "稀有度不合法");
        }
        if (!METRICS.contains(metric)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "解锁指标不合法");
        }
        int threshold = request.metricThreshold() == null ? 1 : request.metricThreshold();
        if (threshold < 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "解锁阈值必须 ≥1");
        }
        entity.setBadgeName(request.badgeName().trim());
        entity.setCategory(category);
        entity.setRarity(rarity);
        entity.setDescription(request.description().trim());
        entity.setMetricCode(metric);
        entity.setMetricThreshold(threshold);
        entity.setRewardExp(request.rewardExp() == null ? 0 : Math.max(0, request.rewardExp()));
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setEnabled(request.enabled() == null || request.enabled());
    }

    private IconPayload validateIcon(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "图标文件不能为空");
        }
        if (file.getSize() > MAX_ICON_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "图标不能超过 512KB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "图标读取失败");
        }
        String extension = detectImageExtension(content);
        if (extension == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "图标仅支持 PNG/JPEG/WebP 格式");
        }
        String contentType = switch (extension) {
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "jpg" -> MediaType.IMAGE_JPEG_VALUE;
            default -> "image/webp";
        };
        return new IconPayload(content, extension, contentType);
    }

    /** 内容嗅探（不信任文件名/Content-Type）：PNG / JPEG / WebP 魔数。 */
    private String detectImageExtension(byte[] content) {
        if (content.length >= 8 && (content[0] & 0xFF) == 0x89 && content[1] == 0x50
                && content[2] == 0x4E && content[3] == 0x47) {
            return "png";
        }
        if (content.length >= 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (content.length >= 12 && content[0] == 'R' && content[1] == 'I' && content[2] == 'F'
                && content[3] == 'F' && content[8] == 'W' && content[9] == 'E' && content[10] == 'B'
                && content[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String value = code.trim().toUpperCase();
        if (!value.matches("^[A-Z][A-Z0-9_]{1,63}$")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "勋章编码需为大写字母开头的字母/数字/下划线组合");
        }
        return value;
    }

    private Map<String, String> categoryNames() {
        return categoryMapper.selectList(new LambdaQueryWrapper<AchievementCategoryEntity>()
                        .orderByAsc(AchievementCategoryEntity::getSortOrder))
                .stream()
                .collect(Collectors.toMap(AchievementCategoryEntity::getCategoryCode,
                        AchievementCategoryEntity::getCategoryName, (left, right) -> left));
    }

    private AdminAchievementBadgeResponse toResponse(AchievementBadgeEntity entity,
                                                     Map<String, String> categoryNames) {
        return new AdminAchievementBadgeResponse(entity.getId(), entity.getBadgeCode(), entity.getBadgeName(),
                entity.getCategory(), categoryNames.get(entity.getCategory()), entity.getRarity(),
                entity.getDescription(), entity.getMetricCode(), entity.getMetricThreshold(),
                entity.getRewardExp(), entity.getSortOrder(), entity.getEnabled(), entity.getIconUrl(),
                entity.getCreatedAt());
    }

    private record IconPayload(byte[] content, String extension, String contentType) {
    }
}
