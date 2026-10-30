package com.studenthub.modules.push.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.common.privacy.PhoneMasker;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.push.content.controller.dto.CreatorCertifyRequest;
import com.studenthub.modules.push.content.controller.dto.CreatorPageQuery;
import com.studenthub.modules.push.content.controller.dto.CreatorPenaltyRequest;
import com.studenthub.modules.push.content.controller.dto.CreatorPenaltyResponse;
import com.studenthub.modules.push.content.controller.dto.CreatorProfileRequest;
import com.studenthub.modules.push.content.controller.dto.CreatorProfileResponse;
import com.studenthub.modules.push.content.entity.CreatorPenaltyEntity;
import com.studenthub.modules.push.content.entity.CreatorProfileEntity;
import com.studenthub.modules.push.content.mapper.CreatorPenaltyMapper;
import com.studenthub.modules.push.content.mapper.CreatorProfileMapper;
import com.studenthub.modules.push.content.service.CreatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 创作者运营实现。
 *
 * <p>账号状态（NORMAL/MUTED/BANNED）是处罚表的派生结果：每次处罚创建/撤销/过期后统一重算，
 * 页面不能直接改写，避免「档案显示正常但处罚仍在生效」这类不一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorServiceImpl implements CreatorService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_EXPIRE_BATCH = 200;
    private static final Set<String> CERT_STATUSES = Set.of("NONE", "PENDING", "APPROVED", "REJECTED", "DISABLED");
    private static final Set<String> PENALTY_TYPES = Set.of("WARN", "MUTE", "BAN");

    private final CreatorProfileMapper profileMapper;
    private final CreatorPenaltyMapper penaltyMapper;
    private final UserMapper userMapper;
    private final AdminCampusContextService campusContextService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public PageResult<CreatorProfileResponse> page(CreatorPageQuery query) {
        int current = Math.max(1, query.pageNumOrDefault());
        int size = Math.max(1, Math.min(query.pageSizeOrDefault(), MAX_PAGE_SIZE));
        LambdaQueryWrapper<CreatorProfileEntity> wrapper = new LambdaQueryWrapper<CreatorProfileEntity>()
                .eq(CreatorProfileEntity::getCampusId, campusId());
        if (StringUtils.hasText(query.certificationStatus())) {
            wrapper.eq(CreatorProfileEntity::getCertificationStatus, normalizeEnum(query.certificationStatus()));
        }
        if (StringUtils.hasText(query.status())) {
            wrapper.eq(CreatorProfileEntity::getStatus, normalizeEnum(query.status()));
        }
        if (query.level() != null) {
            wrapper.eq(CreatorProfileEntity::getLevel, query.level());
        }
        if (StringUtils.hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            if (keyword.matches("\\d{1,19}")) {
                wrapper.eq(CreatorProfileEntity::getUserId, Long.parseLong(keyword));
            } else {
                wrapper.like(CreatorProfileEntity::getDisplayName, keyword);
            }
        }
        wrapper.orderByDesc(CreatorProfileEntity::getId);
        Page<CreatorProfileEntity> page = profileMapper.selectPage(new Page<>(current, size), wrapper);
        List<CreatorProfileEntity> records = page.getRecords();
        Map<Long, UserEntity> users = loadUsers(records);
        Map<Long, Long> penaltyCounts = loadActivePenaltyCounts(records);
        List<CreatorProfileResponse> responses = new ArrayList<>();
        for (CreatorProfileEntity record : records) {
            CreatorProfileResponse response = CreatorProfileResponse.from(
                    record, penaltyCounts.getOrDefault(record.getId(), 0L));
            UserEntity user = record.getUserId() == null ? null : users.get(record.getUserId());
            if (user != null) {
                response = response.withUser(user.getUsername(), PhoneMasker.mask(user.getPhone()));
            }
            responses.add(response);
        }
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), responses);
    }

    @Override
    public CreatorProfileResponse detail(Long id) {
        CreatorProfileEntity profile = requireProfile(id);
        List<CreatorPenaltyResponse> penalties = penaltyMapper.selectList(
                        new LambdaQueryWrapper<CreatorPenaltyEntity>()
                                .eq(CreatorPenaltyEntity::getCreatorId, profile.getId())
                                .orderByDesc(CreatorPenaltyEntity::getId))
                .stream().map(CreatorPenaltyResponse::from).toList();
        long activeCount = penalties.stream().filter(p -> "ACTIVE".equals(p.status())).count();
        CreatorProfileResponse response = CreatorProfileResponse.from(profile, activeCount, penalties);
        UserEntity user = userMapper.selectById(profile.getUserId());
        if (user != null) {
            response = response.withUser(user.getUsername(), PhoneMasker.mask(user.getPhone()));
        }
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreatorProfileResponse createProfile(CreatorProfileRequest request) {
        if (request.userId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "创建创作者档案必须指定用户");
        }
        Long campusId = campusId();
        UserEntity user = userMapper.selectById(request.userId());
        if (user == null || !campusId.equals(user.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在或不在当前校区");
        }
        Long exists = profileMapper.selectCount(new LambdaQueryWrapper<CreatorProfileEntity>()
                .eq(CreatorProfileEntity::getUserId, user.getId()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该用户已有创作者档案");
        }
        CreatorProfileEntity entity = new CreatorProfileEntity();
        entity.setCampusId(campusId);
        entity.setUserId(user.getId());
        entity.setDisplayName(StringUtils.hasText(request.displayName())
                ? request.displayName().trim() : user.getDisplayName());
        entity.setCertificationStatus("NONE");
        entity.setLevel(request.level() == null ? 1 : request.level());
        entity.setScore(request.score() == null ? 0 : request.score());
        entity.setStatus("NORMAL");
        entity.setRemark(request.remark());
        profileMapper.insert(entity);
        log.info("创作者档案已创建, campusId={}, userId={}", campusId, user.getId());
        return detail(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreatorProfileResponse updateProfile(Long id, CreatorProfileRequest request) {
        CreatorProfileEntity entity = requireProfile(id);
        if (StringUtils.hasText(request.displayName())) {
            entity.setDisplayName(request.displayName().trim());
        }
        if (request.level() != null) {
            entity.setLevel(request.level());
        }
        if (request.score() != null) {
            entity.setScore(request.score());
        }
        entity.setRemark(request.remark());
        profileMapper.updateById(entity);
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreatorProfileResponse certify(Long id, CreatorCertifyRequest request) {
        CreatorProfileEntity entity = requireProfile(id);
        String decision = normalizeEnum(request.decision());
        if (decision == null || !CERT_STATUSES.contains(decision)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "认证决定仅支持 APPROVED/REJECTED/PENDING/DISABLED");
        }
        entity.setCertificationStatus(decision);
        entity.setCertificationNote(request.note());
        profileMapper.updateById(entity);
        log.info("创作者认证状态已更新, creatorId={}, decision={}", id, decision);
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreatorPenaltyResponse createPenalty(Long id, CreatorPenaltyRequest request) {
        CreatorProfileEntity profile = requireProfile(id);
        String type = normalizeEnum(request.penaltyType());
        if (type == null || !PENALTY_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "处罚类型仅支持 WARN/MUTE/BAN");
        }
        LocalDateTime startAt = request.startAt() == null ? LocalDateTime.now() : request.startAt();
        LocalDateTime endAt = request.endAt();
        if ("MUTE".equals(type)) {
            if (endAt == null) {
                endAt = startAt.plusHours(24);
            }
            if (!endAt.isAfter(startAt)) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "禁言结束时间必须晚于开始时间");
            }
        }
        if ("BAN".equals(type) && endAt != null && !endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "封禁结束时间必须晚于开始时间（留空表示永久）");
        }
        CreatorPenaltyEntity penalty = new CreatorPenaltyEntity();
        penalty.setCampusId(profile.getCampusId());
        penalty.setCreatorId(profile.getId());
        penalty.setUserId(profile.getUserId());
        penalty.setPenaltyType(type);
        penalty.setReason(request.reason());
        penalty.setEvidenceUrl(request.evidenceUrl());
        penalty.setStartAt(startAt);
        penalty.setEndAt(endAt);
        penalty.setStatus("ACTIVE");
        penalty.setAppealStatus("NONE");
        penalty.setHandledBy(currentUserProvider.currentUserIdOrSystem());
        penalty.setHandledAt(LocalDateTime.now());
        penaltyMapper.insert(penalty);
        recomputeStatus(profile);
        log.info("创作者处罚已创建, creatorId={}, type={}, penaltyId={}", id, type, penalty.getId());
        return CreatorPenaltyResponse.from(penaltyMapper.selectById(penalty.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreatorPenaltyResponse revokePenalty(Long penaltyId) {
        CreatorPenaltyEntity penalty = requirePenalty(penaltyId);
        if (!"ACTIVE".equals(penalty.getStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "仅生效中的处罚可撤销");
        }
        penalty.setStatus("REVOKED");
        penalty.setHandledBy(currentUserProvider.currentUserIdOrSystem());
        penalty.setHandledAt(LocalDateTime.now());
        penaltyMapper.updateById(penalty);
        CreatorProfileEntity profile = profileMapper.selectById(penalty.getCreatorId());
        if (profile != null) {
            recomputeStatus(profile);
        }
        log.info("创作者处罚已撤销, penaltyId={}", penaltyId);
        return CreatorPenaltyResponse.from(penaltyMapper.selectById(penaltyId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreatorPenaltyResponse handleAppeal(Long penaltyId, boolean approve, String result) {
        CreatorPenaltyEntity penalty = requirePenalty(penaltyId);
        if (!"PENDING".equals(penalty.getAppealStatus()) && !"NONE".equals(penalty.getAppealStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "该处罚申诉已处理完成");
        }
        penalty.setAppealStatus(approve ? "APPROVED" : "REJECTED");
        penalty.setAppealResult(result);
        penalty.setHandledBy(currentUserProvider.currentUserIdOrSystem());
        penalty.setHandledAt(LocalDateTime.now());
        if (approve && "ACTIVE".equals(penalty.getStatus())) {
            penalty.setStatus("REVOKED");
        }
        penaltyMapper.updateById(penalty);
        CreatorProfileEntity profile = profileMapper.selectById(penalty.getCreatorId());
        if (profile != null) {
            recomputeStatus(profile);
        }
        log.info("创作者申诉已处理, penaltyId={}, approve={}", penaltyId, approve);
        return CreatorPenaltyResponse.from(penaltyMapper.selectById(penaltyId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireStalePenalties() {
        LocalDateTime now = LocalDateTime.now();
        List<CreatorPenaltyEntity> expired = penaltyMapper.selectList(
                new LambdaQueryWrapper<CreatorPenaltyEntity>()
                        .eq(CreatorPenaltyEntity::getStatus, "ACTIVE")
                        .isNotNull(CreatorPenaltyEntity::getEndAt)
                        .lt(CreatorPenaltyEntity::getEndAt, now)
                        .last("LIMIT " + MAX_EXPIRE_BATCH));
        if (expired.isEmpty()) {
            return 0;
        }
        Set<Long> creatorIds = new java.util.LinkedHashSet<>();
        for (CreatorPenaltyEntity penalty : expired) {
            penalty.setStatus("EXPIRED");
            penaltyMapper.updateById(penalty);
            creatorIds.add(penalty.getCreatorId());
        }
        for (Long creatorId : creatorIds) {
            CreatorProfileEntity profile = profileMapper.selectById(creatorId);
            if (profile != null) {
                recomputeStatus(profile);
            }
        }
        log.info("创作者处罚到期处理完成, count={}", expired.size());
        return expired.size();
    }

    // ---------------------------------------------------------------- 内部

    /** 依据当前生效处罚重算账号状态（BAN > MUTE > NORMAL）。 */
    private void recomputeStatus(CreatorProfileEntity profile) {
        LocalDateTime now = LocalDateTime.now();
        List<CreatorPenaltyEntity> active = penaltyMapper.selectList(
                new LambdaQueryWrapper<CreatorPenaltyEntity>()
                        .eq(CreatorPenaltyEntity::getCreatorId, profile.getId())
                        .eq(CreatorPenaltyEntity::getStatus, "ACTIVE")
                        .le(CreatorPenaltyEntity::getStartAt, now)
                        .and(w -> w.isNull(CreatorPenaltyEntity::getEndAt)
                                .or().gt(CreatorPenaltyEntity::getEndAt, now)));
        CreatorPenaltyEntity ban = active.stream()
                .filter(p -> "BAN".equals(p.getPenaltyType())).findFirst().orElse(null);
        if (ban != null) {
            profile.setStatus("BANNED");
            profile.setBannedReason(ban.getReason());
            profile.setMutedUntil(null);
        } else {
            CreatorPenaltyEntity mute = null;
            for (CreatorPenaltyEntity penalty : active) {
                if (!"MUTE".equals(penalty.getPenaltyType())) {
                    continue;
                }
                if (mute == null) {
                    mute = penalty;
                    continue;
                }
                LocalDateTime current = mute.getEndAt();
                LocalDateTime candidate = penalty.getEndAt();
                if (candidate != null && (current == null || candidate.isAfter(current))) {
                    mute = penalty;
                }
            }
            if (mute != null) {
                profile.setStatus("MUTED");
                profile.setMutedUntil(mute.getEndAt());
                profile.setBannedReason(null);
            } else {
                profile.setStatus("NORMAL");
                profile.setMutedUntil(null);
                profile.setBannedReason(null);
            }
        }
        profileMapper.updateById(profile);
    }

    private Map<Long, UserEntity> loadUsers(List<CreatorProfileEntity> records) {
        List<Long> userIds = records.stream().map(CreatorProfileEntity::getUserId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserEntity> result = new LinkedHashMap<>();
        for (UserEntity user : userMapper.selectBatchIds(userIds)) {
            result.put(user.getId(), user);
        }
        return result;
    }

    private Map<Long, Long> loadActivePenaltyCounts(List<CreatorProfileEntity> records) {
        List<Long> creatorIds = records.stream().map(CreatorProfileEntity::getId).toList();
        if (creatorIds.isEmpty()) {
            return Map.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<CreatorPenaltyEntity> penalties = penaltyMapper.selectList(
                new LambdaQueryWrapper<CreatorPenaltyEntity>()
                        .in(CreatorPenaltyEntity::getCreatorId, creatorIds)
                        .eq(CreatorPenaltyEntity::getStatus, "ACTIVE")
                        .and(w -> w.isNull(CreatorPenaltyEntity::getEndAt)
                                .or().gt(CreatorPenaltyEntity::getEndAt, now)));
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (CreatorPenaltyEntity penalty : penalties) {
            counts.merge(penalty.getCreatorId(), 1L, Long::sum);
        }
        return counts;
    }

    private CreatorProfileEntity requireProfile(Long id) {
        CreatorProfileEntity entity = id == null ? null : profileMapper.selectById(id);
        if (entity == null || !campusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "创作者档案不存在");
        }
        return entity;
    }

    private CreatorPenaltyEntity requirePenalty(Long penaltyId) {
        CreatorPenaltyEntity entity = penaltyId == null ? null : penaltyMapper.selectById(penaltyId);
        if (entity == null || !campusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "处罚记录不存在");
        }
        return entity;
    }

    private Long campusId() {
        return campusContextService.currentCampusId();
    }

    private String normalizeEnum(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
