package com.studenthub.modules.achievement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.util.Digests;
import com.studenthub.modules.achievement.controller.dto.AchievementActivatedItem;
import com.studenthub.modules.achievement.controller.dto.AchievementCatalogItem;
import com.studenthub.modules.achievement.controller.dto.AchievementCatalogResponse;
import com.studenthub.modules.achievement.controller.dto.AchievementCategoryItem;
import com.studenthub.modules.achievement.controller.dto.AchievementProgressItem;
import com.studenthub.modules.achievement.controller.dto.AchievementStateResponse;
import com.studenthub.modules.achievement.entity.AchievementBadgeEntity;
import com.studenthub.modules.achievement.entity.AchievementCategoryEntity;
import com.studenthub.modules.achievement.entity.UserAchievementBadgeEntity;
import com.studenthub.modules.achievement.mapper.AchievementBadgeMapper;
import com.studenthub.modules.achievement.mapper.AchievementCategoryMapper;
import com.studenthub.modules.achievement.mapper.UserAchievementBadgeMapper;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 徽章方案B：字典热更新与高频用户态拆分。
 * <p>catalog：带版本号的完整展示字典（分类名/排序/新增码图标），客户端按版本缓存到本地；
 * state：每次启动拉取的轻量用户态（已激活 + 当前佩戴 + 进度 + 版本），客户端比对版本决定是否重拉字典。</p>
 * <p>版本由「分类 + 勋章全量内容（含启用态/图标）」哈希派生，管理端任何增删改/启停都会产生新版本，
 * 不依赖编译期常量，也不需要在管理端额外维护版本号。</p>
 */
@Service
@RequiredArgsConstructor
public class AchievementCatalogService {

    private final AchievementBadgeMapper badgeMapper;
    private final AchievementCategoryMapper categoryMapper;
    private final UserAchievementBadgeMapper userBadgeMapper;
    private final UserMapper userMapper;
    private final AchievementService achievementService;

    /** 字典接口：版本一致时只回 changed=false，不下发列表。 */
    public AchievementCatalogResponse catalog(String clientVersion) {
        Snapshot snapshot = loadSnapshot();
        if (clientVersion != null && clientVersion.equals(snapshot.version())) {
            return new AchievementCatalogResponse(snapshot.version(), false, null, null);
        }
        List<AchievementCategoryItem> categories = snapshot.categories().stream()
                .filter(category -> Boolean.TRUE.equals(category.getEnabled()))
                .map(category -> new AchievementCategoryItem(category.getCategoryCode(), category.getCategoryName(),
                        category.getSortOrder()))
                .toList();
        List<AchievementCatalogItem> badges = snapshot.badges().stream()
                .filter(badge -> Boolean.TRUE.equals(badge.getEnabled()))
                .map(badge -> toCatalogItem(badge, snapshot.version()))
                .toList();
        return new AchievementCatalogResponse(snapshot.version(), true, categories, badges);
    }

    /** 用户态接口：懒评估后返回已激活/佩戴/进度。 */
    public AchievementStateResponse state(Long userId) {
        achievementService.evaluate(userId);
        Snapshot snapshot = loadSnapshot();
        Map<String, UserAchievementBadgeEntity> byCode = userBadgeMapper.selectList(
                        new LambdaQueryWrapper<UserAchievementBadgeEntity>()
                                .eq(UserAchievementBadgeEntity::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(UserAchievementBadgeEntity::getBadgeCode, Function.identity(),
                        (left, right) -> left));
        List<AchievementBadgeEntity> enabled = snapshot.badges().stream()
                .filter(badge -> Boolean.TRUE.equals(badge.getEnabled()))
                .toList();
        List<AchievementActivatedItem> activated = enabled.stream()
                .filter(badge -> {
                    UserAchievementBadgeEntity record = byCode.get(badge.getBadgeCode());
                    return record != null && record.getUnlockedAt() != null;
                })
                .map(badge -> new AchievementActivatedItem(badge.getBadgeCode(),
                        byCode.get(badge.getBadgeCode()).getUnlockedAt()))
                .toList();
        List<AchievementProgressItem> progress = enabled.stream()
                .map(badge -> new AchievementProgressItem(badge.getBadgeCode(),
                        progressOf(byCode.get(badge.getBadgeCode()))))
                .toList();
        UserEntity user = userMapper.selectById(userId);
        String rawWornBadgeCode = user == null ? null : user.getWornBadgeCode();
        // 防御：历史数据里佩戴码可能已停用/删除（V52 已修复一次，这里兜底）
        String wornBadgeCode = rawWornBadgeCode != null
                && enabled.stream().noneMatch(badge -> rawWornBadgeCode.equals(badge.getBadgeCode()))
                ? null : rawWornBadgeCode;
        return new AchievementStateResponse(snapshot.version(), wornBadgeCode, activated, progress);
    }

    /** 当前字典版本（旧 /badges 兼容接口与客户端比版本使用）。 */
    public String currentVersion() {
        return loadSnapshot().version();
    }

    /** 分类编码 → 展示名（管理端响应组装用）。 */
    public Map<String, String> categoryNames() {
        return categoryMapper.selectList(new LambdaQueryWrapper<AchievementCategoryEntity>()
                        .orderByAsc(AchievementCategoryEntity::getSortOrder)
                        .orderByAsc(AchievementCategoryEntity::getId))
                .stream()
                .collect(Collectors.toMap(AchievementCategoryEntity::getCategoryCode,
                        AchievementCategoryEntity::getCategoryName, (left, right) -> left));
    }

    private Snapshot loadSnapshot() {
        List<AchievementCategoryEntity> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<AchievementCategoryEntity>()
                        .orderByAsc(AchievementCategoryEntity::getSortOrder)
                        .orderByAsc(AchievementCategoryEntity::getId));
        List<AchievementBadgeEntity> badges = badgeMapper.selectList(
                new LambdaQueryWrapper<AchievementBadgeEntity>()
                        .orderByAsc(AchievementBadgeEntity::getSortOrder)
                        .orderByAsc(AchievementBadgeEntity::getId));
        return new Snapshot(categories, badges, versionOf(categories, badges));
    }

    /**
     * 版本哈希：包含分类与勋章的全部展示/规则字段以及启用态；任何编辑或启停都会变化。
     * 不包含 updated_at，保证无实际变化的保存不产生新版本、不触发客户端重拉。
     */
    private String versionOf(List<AchievementCategoryEntity> categories, List<AchievementBadgeEntity> badges) {
        StringBuilder canonical = new StringBuilder();
        for (AchievementCategoryEntity category : categories) {
            canonical.append("C|").append(nullSafe(category.getCategoryCode()))
                    .append('|').append(nullSafe(category.getCategoryName()))
                    .append('|').append(nullSafe(category.getSortOrder()))
                    .append('|').append(Boolean.TRUE.equals(category.getEnabled()))
                    .append('\n');
        }
        for (AchievementBadgeEntity badge : badges) {
            canonical.append("B|").append(nullSafe(badge.getBadgeCode()))
                    .append('|').append(nullSafe(badge.getBadgeName()))
                    .append('|').append(nullSafe(badge.getCategory()))
                    .append('|').append(nullSafe(badge.getRarity()))
                    .append('|').append(nullSafe(badge.getDescription()))
                    .append('|').append(nullSafe(badge.getMetricCode()))
                    .append('|').append(nullSafe(badge.getMetricThreshold()))
                    .append('|').append(nullSafe(badge.getRewardExp()))
                    .append('|').append(nullSafe(badge.getSortOrder()))
                    .append('|').append(Boolean.TRUE.equals(badge.getEnabled()))
                    .append('|').append(nullSafe(badge.getIconUrl()))
                    .append('\n');
        }
        return Digests.sha256Hex(canonical.toString()).substring(0, 16);
    }

    private AchievementCatalogItem toCatalogItem(AchievementBadgeEntity badge, String version) {
        return new AchievementCatalogItem(badge.getBadgeCode(), badge.getBadgeName(), badge.getCategory(),
                badge.getRarity(), badge.getDescription(), badge.getMetricThreshold(), badge.getRewardExp(),
                badge.getSortOrder(), versionedIconUrl(badge.getIconUrl(), version));
    }

    /** 图标地址追加版本参数，管理端替换图标后客户端能拿到新图（本地缓存失效）。 */
    private String versionedIconUrl(String iconUrl, String version) {
        if (iconUrl == null || iconUrl.isBlank()) {
            return null;
        }
        return iconUrl.contains("?") ? iconUrl + "&v=" + version : iconUrl + "?v=" + version;
    }

    private int progressOf(UserAchievementBadgeEntity record) {
        return record == null || record.getCurrentProgress() == null ? 0 : record.getCurrentProgress();
    }

    private String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }

    private record Snapshot(List<AchievementCategoryEntity> categories, List<AchievementBadgeEntity> badges,
                            String version) {
    }
}
