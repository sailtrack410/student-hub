package com.studenthub.modules.achievement.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.achievement.controller.dto.AchievementBadgeItem;
import com.studenthub.modules.achievement.controller.dto.AchievementProfileResponse;
import com.studenthub.modules.achievement.entity.AchievementBadgeEntity;
import com.studenthub.modules.achievement.entity.UserAchievementBadgeEntity;
import com.studenthub.modules.achievement.mapper.AchievementBadgeMapper;
import com.studenthub.modules.achievement.mapper.UserAchievementBadgeMapper;
import com.studenthub.modules.achievement.service.AchievementService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成就勋章（2026-10-17 定稿）：打开即评估（懒评估）；响应只回 badgeCode/进度/佩戴态，
 * 图标随包本地匹配 assets/badges/$code.png，badgeVersion 热更新后门随字典接口下发。
 */
@RestController
@RequestMapping("/api/app/achievements")
@Tag(name = "小程序-成就勋章", description = "等级与勋章：打开即评估，勋章墙全量返回进度")
@RequiredArgsConstructor
public class AppAchievementController {

    private final AchievementService achievementService;
    private final AchievementBadgeMapper badgeMapper;
    private final UserAchievementBadgeMapper userBadgeMapper;
    private final UserMapper userMapper;
    private final CurrentUserProvider currentUserProvider;

    /** 字典版本：目录变更时递增（迁移注释记录），客户端据此决定是否刷新随包 JSON */
    private static final int BADGE_VERSION = 1;

    @GetMapping("/profile")
    @Operation(summary = "成长档案", description = "先做一次懒评估再返回：等级、累计经验、进度、已获勋章数与最近 3 枚。",
            operationId = "getAchievementProfile")
    public Result<AchievementProfileResponse> profile() {
        AuthenticatedUser user = currentUserProvider.requireCurrentUser();
        achievementService.evaluate(user.userId());
        return Result.success(buildProfile(user.userId()));
    }

    @GetMapping("/badges")
    @Operation(summary = "勋章墙", description = "全部勋章 + 是否已获得 + 进度（未获得也返回）；先懒评估保证进度最新。",
            operationId = "getAchievementBadges")
    public Result<Map<String, Object>> badges() {
        AuthenticatedUser user = currentUserProvider.requireCurrentUser();
        achievementService.evaluate(user.userId());
        List<AchievementBadgeEntity> badges = badgeMapper.selectList(
                new LambdaQueryWrapper<AchievementBadgeEntity>()
                        .eq(AchievementBadgeEntity::getEnabled, true)
                        .orderByAsc(AchievementBadgeEntity::getSortOrder));
        Map<String, UserAchievementBadgeEntity> byCode = userBadgeMapper.selectList(
                        new LambdaQueryWrapper<UserAchievementBadgeEntity>()
                                .eq(UserAchievementBadgeEntity::getUserId, user.userId()))
                .stream().collect(Collectors.toMap(UserAchievementBadgeEntity::getBadgeCode, Function.identity()));
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
        return Result.success(Map.of("badgeVersion", BADGE_VERSION, "badges", items));
    }

    @PostMapping("/wear")
    @Operation(summary = "佩戴勋章", description = "badgeCode 传 null 表示卸下；佩戴须已解锁。",
            operationId = "wearBadge")
    public Result<Void> wear(@RequestBody Map<String, String> body) {
        AuthenticatedUser user = currentUserProvider.requireCurrentUser();
        String badgeCode = body.get("badgeCode");
        achievementService.wear(user.userId(), badgeCode == null || badgeCode.isBlank() ? null : badgeCode.trim());
        return Result.success();
    }

    private AchievementProfileResponse buildProfile(Long userId) {
        // evaluate 已在调用方执行；此处直接重算展示值（与评估同一口径）
        int totalExp = achievementService.totalExpOf(userId);
        int level = AchievementService.levelOf(totalExp);
        int nextIdx = level < AchievementService.maxLevel() ? level : -1;
        List<UserAchievementBadgeEntity> records = userBadgeMapper.selectList(
                new LambdaQueryWrapper<UserAchievementBadgeEntity>()
                        .eq(UserAchievementBadgeEntity::getUserId, userId)
                        .isNotNull(UserAchievementBadgeEntity::getUnlockedAt));
        long badgeCount = records.size();
        long totalBadge = badgeMapper.selectCount(new LambdaQueryWrapper<AchievementBadgeEntity>()
                .eq(AchievementBadgeEntity::getEnabled, true));
        List<AchievementProfileResponse.RecentBadge> recent = records.stream()
                .sorted(java.util.Comparator.comparing(UserAchievementBadgeEntity::getUnlockedAt).reversed())
                .limit(3)
                .map(record -> {
                    AchievementBadgeEntity badge = badgeMapper.selectOne(
                            new LambdaQueryWrapper<AchievementBadgeEntity>()
                                    .eq(AchievementBadgeEntity::getBadgeCode, record.getBadgeCode()));
                    return new AchievementProfileResponse.RecentBadge(record.getBadgeCode(),
                            badge != null ? badge.getBadgeName() : record.getBadgeCode(), record.getUnlockedAt());
                }).toList();
        UserEntity user = userMapper.selectById(userId);
        String worn = user != null ? user.getWornBadgeCode() : null;
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
