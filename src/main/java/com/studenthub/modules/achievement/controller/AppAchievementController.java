package com.studenthub.modules.achievement.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.Result;
import com.studenthub.modules.achievement.controller.dto.AchievementBadgeItem;
import com.studenthub.modules.achievement.controller.dto.AchievementCatalogResponse;
import com.studenthub.modules.achievement.controller.dto.AchievementProfileResponse;
import com.studenthub.modules.achievement.controller.dto.AchievementStateResponse;
import com.studenthub.modules.achievement.controller.dto.WearBadgeRequest;
import com.studenthub.modules.achievement.entity.AchievementBadgeEntity;
import com.studenthub.modules.achievement.entity.UserAchievementBadgeEntity;
import com.studenthub.modules.achievement.mapper.AchievementBadgeMapper;
import com.studenthub.modules.achievement.mapper.UserAchievementBadgeMapper;
import com.studenthub.modules.achievement.service.AchievementCatalogService;
import com.studenthub.modules.achievement.service.AchievementService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成就勋章（2026-10-19 方案B）：字典与用户态拆分。
 * <ul>
 *   <li>{@code GET /catalog}：带版本号的展示字典（分类名/排序/新增码图标），客户端按版本缓存本地；
 *       管理端增删改后版本自动变化，无需发版即可展示新勋章。</li>
 *   <li>{@code GET /state}：高频轻量用户态（已激活/当前佩戴/进度/字典版本），客户端用它判断是否重拉字典。</li>
 *   <li>旧 {@code GET /badges} 保留兼容，客户端不再依赖它做字典。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/app/achievements")
@Tag(name = "小程序-成就勋章", description = "等级与勋章：字典热更新、用户态与佩戴管理")
@RequiredArgsConstructor
public class AppAchievementController {

    private final AchievementService achievementService;
    private final AchievementCatalogService catalogService;
    private final AchievementBadgeMapper badgeMapper;
    private final UserAchievementBadgeMapper userBadgeMapper;
    private final UserMapper userMapper;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/catalog")
    @Operation(summary = "勋章字典（带版本热更新）",
            description = "客户端携带本地 catalogVersion；版本一致时 changed=false 且不下发列表；"
                    + "版本变化时下发启用分类与勋章字典（含 iconUrl），客户端保存本地。",
            operationId = "getAchievementCatalog")
    public Result<AchievementCatalogResponse> catalog(@RequestParam(required = false) String version) {
        return Result.success(catalogService.catalog(version));
    }

    @GetMapping("/state")
    @Operation(summary = "徽章用户态（激活/佩戴/进度）",
            description = "先做一次懒评估，再返回 catalogVersion、当前佩戴勋章、已激活勋章与全部启用勋章的当前进度。"
                    + "客户端每次启动调用；catalogVersion 与本地不一致时再拉 /catalog。",
            operationId = "getAchievementState")
    public Result<AchievementStateResponse> state() {
        return Result.success(catalogService.state(currentUserProvider.requireCurrentUser().userId()));
    }

    @GetMapping("/profile")
    @Operation(summary = "成长档案", description = "先做一次懒评估再返回：等级、累计经验、进度、已获勋章数与最近 3 枚（仅统计启用勋章）。",
            operationId = "getAchievementProfile")
    public Result<AchievementProfileResponse> profile() {
        AuthenticatedUser user = currentUserProvider.requireCurrentUser();
        achievementService.evaluate(user.userId());
        return Result.success(buildProfile(user.userId()));
    }

    /**
     * 旧版勋章墙接口（兼容保留）：与客户端本地字典可能重复，客户端应改用 /catalog + /state。
     * 响应新增 catalogVersion，供仍在使用旧接口的客户端判断字典是否变化；badgeVersion 已废弃。
     */
    @GetMapping("/badges")
    @Operation(summary = "勋章墙（兼容旧版，建议改用 /catalog + /state）", deprecated = true,
            description = "返回全部启用勋章 + 是否已获得 + 进度；badgeVersion 已废弃，catalogVersion 为当前字典版本。",
            operationId = "getAchievementBadges")
    public Result<Map<String, Object>> badges() {
        AuthenticatedUser user = currentUserProvider.requireCurrentUser();
        achievementService.evaluate(user.userId());
        List<AchievementBadgeEntity> badges = badgeMapper.selectList(
                new LambdaQueryWrapper<AchievementBadgeEntity>()
                        .eq(AchievementBadgeEntity::getEnabled, true)
                        .orderByAsc(AchievementBadgeEntity::getSortOrder)
                        .orderByAsc(AchievementBadgeEntity::getId));
        Map<String, UserAchievementBadgeEntity> byCode = userBadgeMapper.selectList(
                        new LambdaQueryWrapper<UserAchievementBadgeEntity>()
                                .eq(UserAchievementBadgeEntity::getUserId, user.userId()))
                .stream().collect(Collectors.toMap(UserAchievementBadgeEntity::getBadgeCode, Function.identity(),
                        (left, right) -> left));
        List<AchievementBadgeItem> items = badges.stream()
                .map(badge -> {
                    UserAchievementBadgeEntity record = byCode.get(badge.getBadgeCode());
                    boolean unlocked = record != null && record.getUnlockedAt() != null;
                    return new AchievementBadgeItem(badge.getBadgeCode(), badge.getBadgeName(),
                            badge.getCategory(), badge.getRarity(), badge.getDescription(),
                            badge.getMetricThreshold(), badge.getRewardExp(), unlocked,
                            unlocked ? record.getUnlockedAt() : null,
                            record != null ? record.getCurrentProgress() : 0);
                }).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("catalogVersion", catalogService.currentVersion());
        payload.put("badgeVersion", 1);
        payload.put("badges", items);
        return Result.success(payload);
    }

    @PostMapping("/wear")
    @Operation(summary = "佩戴/卸下勋章",
            description = "action=WEAR 佩戴 / UNWEAR 卸下；缺省时 badgeCode 非空=佩戴、为空=卸下（兼容旧客户端）。"
                    + "佩戴须已解锁且勋章处于启用状态。",
            operationId = "wearBadge")
    public Result<Void> wear(@RequestBody @Valid WearBadgeRequest request) {
        achievementService.wear(currentUserProvider.requireCurrentUser().userId(),
                request.badgeCode(), request.action());
        return Result.success(null);
    }

    private AchievementProfileResponse buildProfile(Long userId) {
        // evaluate 已在调用方执行；此处直接重算展示值（与评估同一口径）
        int totalExp = achievementService.totalExpOf(userId);
        int level = AchievementService.levelOf(totalExp);
        int nextIdx = level < AchievementService.maxLevel() ? level : -1;
        List<AchievementBadgeEntity> enabledBadges = badgeMapper.selectList(
                new LambdaQueryWrapper<AchievementBadgeEntity>()
                        .eq(AchievementBadgeEntity::getEnabled, true)
                        .orderByAsc(AchievementBadgeEntity::getSortOrder));
        Map<String, AchievementBadgeEntity> badgeByCode = enabledBadges.stream()
                .collect(Collectors.toMap(AchievementBadgeEntity::getBadgeCode, Function.identity(),
                        (left, right) -> left));
        List<UserAchievementBadgeEntity> records = userBadgeMapper.selectList(
                        new LambdaQueryWrapper<UserAchievementBadgeEntity>()
                                .eq(UserAchievementBadgeEntity::getUserId, userId)
                                .isNotNull(UserAchievementBadgeEntity::getUnlockedAt))
                .stream()
                .filter(record -> badgeByCode.containsKey(record.getBadgeCode()))
                .toList();
        long badgeCount = records.size();
        long totalBadge = enabledBadges.size();
        List<AchievementProfileResponse.RecentBadge> recent = records.stream()
                .sorted(java.util.Comparator.comparing(UserAchievementBadgeEntity::getUnlockedAt).reversed())
                .limit(3)
                .map(record -> new AchievementProfileResponse.RecentBadge(record.getBadgeCode(),
                        badgeByCode.get(record.getBadgeCode()).getBadgeName(), record.getUnlockedAt()))
                .toList();
        UserEntity user = userMapper.selectById(userId);
        String worn = user != null ? user.getWornBadgeCode() : null;
        if (worn != null && !badgeByCode.containsKey(worn)) {
            worn = null;
        }
        return new AchievementProfileResponse(level,
                AchievementService.levelName(level), totalExp,
                nextIdx > 0 ? nextIdx + 1 : null,
                nextIdx > 0 ? AchievementService.levelName(nextIdx + 1) : null,
                nextIdx > 0 ? AchievementService.levelExp(nextIdx + 1) : null,
                nextIdx > 0 ? AchievementService.levelExp(nextIdx + 1) - totalExp : null,
                progressPercent(totalExp, level),
                badgeCount, totalBadge, worn, recent);
    }

    private int progressPercent(int totalExp, int level) {
        if (level >= AchievementService.maxLevel()) {
            return 100;
        }
        int lower = AchievementService.levelExp(level);
        int upper = AchievementService.levelExp(level + 1);
        if (upper <= lower) {
            return 100;
        }
        return Math.min(100, Math.max(0, (totalExp - lower) * 100 / (upper - lower)));
    }
}
