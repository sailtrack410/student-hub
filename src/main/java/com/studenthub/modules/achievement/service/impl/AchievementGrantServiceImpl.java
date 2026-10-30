package com.studenthub.modules.achievement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.achievement.controller.dto.AdminAchievementGrantRequest;
import com.studenthub.modules.achievement.controller.dto.AdminAchievementGrantResponse;
import com.studenthub.modules.achievement.entity.AchievementBadgeEntity;
import com.studenthub.modules.achievement.entity.UserAchievementBadgeEntity;
import com.studenthub.modules.achievement.mapper.AchievementBadgeMapper;
import com.studenthub.modules.achievement.mapper.UserAchievementBadgeMapper;
import com.studenthub.modules.achievement.service.AchievementGrantService;
import com.studenthub.modules.audit.aspect.AuditTrailContext;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成就发放记录实现：分页按用户/勋章过滤，手动发放写 MANUAL 来源并清空佩戴态校验依赖的进度，
 * 撤销删除记录并清理佩戴态，避免 /auth/me 返回失效勋章编码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementGrantServiceImpl implements AchievementGrantService {

    public static final String SOURCE_AUTO = "AUTO";
    public static final String SOURCE_MANUAL = "MANUAL";

    private final UserAchievementBadgeMapper userBadgeMapper;
    private final AchievementBadgeMapper badgeMapper;
    private final UserMapper userMapper;

    @Override
    public PageResult<AdminAchievementGrantResponse> page(int pageNum, int pageSize, Long userId,
                                                          String badgeCode) {
        int current = Math.max(1, pageNum);
        int size = Math.max(1, Math.min(pageSize, 100));
        String code = StringUtils.hasText(badgeCode) ? badgeCode.trim().toUpperCase() : null;
        Page<UserAchievementBadgeEntity> page = userBadgeMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<UserAchievementBadgeEntity>()
                        .eq(userId != null, UserAchievementBadgeEntity::getUserId, userId)
                        .eq(code != null, UserAchievementBadgeEntity::getBadgeCode, code)
                        .isNotNull(UserAchievementBadgeEntity::getUnlockedAt)
                        .orderByDesc(UserAchievementBadgeEntity::getUnlockedAt)
                        .orderByDesc(UserAchievementBadgeEntity::getId));
        Set<Long> userIds = new LinkedHashSet<>();
        page.getRecords().forEach(record -> userIds.add(record.getUserId()));
        Map<Long, UserEntity> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity(), (a, b) -> a));
        Map<String, String> badgeNames = badgeMapper.selectList(null).stream()
                .collect(Collectors.toMap(AchievementBadgeEntity::getBadgeCode,
                        AchievementBadgeEntity::getBadgeName, (a, b) -> a));
        List<AdminAchievementGrantResponse> records = page.getRecords().stream().map(record -> {
            UserEntity user = users.get(record.getUserId());
            return new AdminAchievementGrantResponse(record.getId(), record.getUserId(),
                    user == null ? "用户" : user.getDisplayName(), record.getBadgeCode(),
                    badgeNames.getOrDefault(record.getBadgeCode(), record.getBadgeCode()),
                    record.getCurrentProgress(), record.getUnlockedAt(),
                    record.getGrantSource() == null ? SOURCE_AUTO : record.getGrantSource());
        }).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAchievementGrantResponse grant(AdminAchievementGrantRequest request) {
        String code = request.badgeCode().trim().toUpperCase();
        AchievementBadgeEntity badge = badgeMapper.selectOne(new LambdaQueryWrapper<AchievementBadgeEntity>()
                .eq(AchievementBadgeEntity::getBadgeCode, code).last("LIMIT 1"));
        if (badge == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "勋章不存在：" + code);
        }
        if (!Boolean.TRUE.equals(badge.getEnabled())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "勋章已停用，不能发放");
        }
        UserEntity user = userMapper.selectById(request.userId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        UserAchievementBadgeEntity existing = userBadgeMapper.selectOne(
                new LambdaQueryWrapper<UserAchievementBadgeEntity>()
                        .eq(UserAchievementBadgeEntity::getUserId, request.userId())
                        .eq(UserAchievementBadgeEntity::getBadgeCode, code)
                        .last("LIMIT 1"));
        if (existing != null && existing.getUnlockedAt() != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该用户已拥有该勋章，无需重复发放");
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("userId", request.userId());
        before.put("badgeCode", code);
        before.put("grantSource", existing == null ? null : existing.getGrantSource());
        before.put("unlockedAt", existing == null ? null : existing.getUnlockedAt());
        AuditTrailContext.setBefore(before);

        LocalDateTime now = LocalDateTime.now();
        UserAchievementBadgeEntity entity = existing == null ? new UserAchievementBadgeEntity() : existing;
        entity.setUserId(request.userId());
        entity.setBadgeCode(code);
        entity.setCurrentProgress(badge.getMetricThreshold() == null ? 1 : badge.getMetricThreshold());
        entity.setUnlockedAt(now);
        entity.setNotified(false);
        entity.setGrantSource(SOURCE_MANUAL);
        if (existing == null) {
            userBadgeMapper.insert(entity);
        } else {
            userBadgeMapper.updateById(entity);
        }
        log.info("成就手动发放, userId={}, badgeCode={}, operatorNote={}", request.userId(), code,
                request.note());
        return new AdminAchievementGrantResponse(entity.getId(), entity.getUserId(), user.getDisplayName(),
                code, badge.getBadgeName(), entity.getCurrentProgress(), entity.getUnlockedAt(), SOURCE_MANUAL);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        UserAchievementBadgeEntity record = userBadgeMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "发放记录不存在");
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("recordId", record.getId());
        before.put("userId", record.getUserId());
        before.put("badgeCode", record.getBadgeCode());
        before.put("grantSource", record.getGrantSource());
        before.put("unlockedAt", record.getUnlockedAt());
        AuditTrailContext.setBefore(before);

        userBadgeMapper.deleteById(id);
        // 撤销后如果用户正在佩戴该勋章，同步清空佩戴态
        userMapper.clearWornBadgeByCode(record.getBadgeCode());
        log.info("成就发放已撤销, recordId={}, userId={}, badgeCode={}", id, record.getUserId(),
                record.getBadgeCode());
    }
}
