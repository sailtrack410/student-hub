package com.studenthub.modules.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.controller.dto.AdminOfficialAccountPageQuery;
import com.studenthub.modules.community.controller.dto.AdminOfficialAccountRequest;
import com.studenthub.modules.community.controller.dto.AdminOfficialAccountResponse;
import com.studenthub.modules.community.mapper.CommunityAccountReferenceMapper;
import com.studenthub.modules.community.service.CommunityOfficialAccountService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;

/**
 * 管理端官方账号实现：账号挂在 sys_user 上（V66 account_type），仅充当社区发布主体，不参与登录。
 * 官方抓取号必须保持 OFFICIAL_AUTO（已绑定抓取来源的账号禁止改成其它类型）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityOfficialAccountServiceImpl implements CommunityOfficialAccountService {

    public static final String TYPE_NORMAL = "NORMAL";
    public static final String TYPE_OFFICIAL_AUTO = "OFFICIAL_AUTO";
    public static final String TYPE_OFFICIAL_HUMAN = "OFFICIAL_HUMAN";
    public static final String TYPE_AD_SERVICE = "AD_SERVICE";

    private static final Set<String> ACCOUNT_TYPES = Set.of(
            TYPE_OFFICIAL_AUTO, TYPE_OFFICIAL_HUMAN, TYPE_AD_SERVICE);
    private static final Set<String> STATUSES = Set.of("ENABLED", "DISABLED");
    private static final Set<String> VERIFICATION_STATUSES = Set.of(
            "NONE", "PENDING", "APPROVED", "REJECTED", "DISABLED");
    private static final int MAX_PAGE_SIZE = 50;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final CommunityAccountReferenceMapper accountReferenceMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AdminCampusContextService campusContextService;

    @Override
    public PageResult<AdminOfficialAccountResponse> page(AdminOfficialAccountPageQuery query) {
        int current = Math.max(1, query.pageNumOrDefault());
        int size = Math.max(1, Math.min(query.pageSizeOrDefault(), MAX_PAGE_SIZE));
        String accountType = normalize(query.accountType());
        if (accountType != null && !ACCOUNT_TYPES.contains(accountType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "账号类型仅支持 OFFICIAL_AUTO/OFFICIAL_HUMAN/AD_SERVICE");
        }
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getCampusId, campusId())
                .ne(UserEntity::getAccountType, TYPE_NORMAL)
                .eq(accountType != null, UserEntity::getAccountType, accountType)
                .eq(StringUtils.hasText(query.status()), UserEntity::getStatus, normalize(query.status()))
                .eq(StringUtils.hasText(query.verificationStatus()), UserEntity::getVerificationStatus,
                        normalize(query.verificationStatus()))
                .and(StringUtils.hasText(query.keyword()), w -> {
                    String keyword = query.keyword().trim();
                    w.like(UserEntity::getDisplayName, keyword)
                            .or().like(UserEntity::getUsername, keyword)
                            .or().like(UserEntity::getAccountOperator, keyword);
                })
                .orderByDesc(UserEntity::getId);
        Page<UserEntity> page = userMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(AdminOfficialAccountResponse::from).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOfficialAccountResponse create(AdminOfficialAccountRequest request) {
        String accountType = requireAccountType(request.accountType());
        String status = normalizeStatus(request.status());
        String verification = normalizeVerification(request.verificationStatus());
        String displayName = request.displayName().trim();
        String operatorName = trimmed(request.operatorName());
        if (TYPE_OFFICIAL_HUMAN.equals(accountType) && operatorName == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "人工运营号必须填写运营人");
        }
        String username = StringUtils.hasText(request.username())
                ? request.username().trim() : generateUsername();
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "用户名已存在");
        }
        UserEntity entity = new UserEntity();
        entity.setCampusId(campusId());
        entity.setUsername(username);
        entity.setDisplayName(displayName);
        entity.setIdentityType("ADMIN");
        entity.setAuthorizationStatus("AUTHORIZED");
        entity.setStatus(status);
        entity.setAccountType(accountType);
        entity.setAccountOperator(operatorName);
        entity.setAccountDescription(trimmed(request.description()));
        entity.setVerificationStatus(verification);
        boolean official = isOfficialAccountType(accountType);
        entity.setOfficial(official);
        entity.setOfficialTitle(resolveOfficialTitle(request.officialTitle(), displayName, official));
        userMapper.insert(entity);
        log.info("创建官方账号, userId={}, username={}, accountType={}, operator={}",
                entity.getId(), username, accountType,
                currentUserProvider.currentUserIdOrSystem());
        return AdminOfficialAccountResponse.from(userMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOfficialAccountResponse update(Long userId, AdminOfficialAccountRequest request) {
        UserEntity user = requireOfficialAccount(userId);
        String accountType = requireAccountType(request.accountType());
        if (!TYPE_OFFICIAL_AUTO.equals(accountType)
                && accountReferenceMapper.countFeedSourcesByAuthor(userId) > 0) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID,
                    "该账号已绑定官方抓取来源，必须保持 OFFICIAL_AUTO 类型");
        }
        String status = normalizeStatus(request.status());
        String verification = normalizeVerification(request.verificationStatus());
        String displayName = request.displayName().trim();
        String operatorName = trimmed(request.operatorName());
        if (TYPE_OFFICIAL_HUMAN.equals(accountType) && operatorName == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "人工运营号必须填写运营人");
        }
        boolean official = isOfficialAccountType(accountType);
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setDisplayName(displayName);
        update.setAccountType(accountType);
        update.setAccountOperator(operatorName);
        update.setAccountDescription(trimmed(request.description()));
        update.setVerificationStatus(verification);
        update.setStatus(status);
        update.setOfficial(official);
        update.setOfficialTitle(resolveOfficialTitle(request.officialTitle(), displayName, official));
        update.setVersion(user.getVersion());
        userMapper.updateById(update);
        log.info("更新官方账号, userId={}, accountType={}, operatorId={}", userId, accountType,
                currentUserProvider.currentUserIdOrSystem());
        return AdminOfficialAccountResponse.from(userMapper.selectById(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOfficialAccountResponse updateStatus(Long userId, String status) {
        String normalized = normalizeStatus(status);
        UserEntity user = requireOfficialAccount(userId);
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setStatus(normalized);
        update.setVersion(user.getVersion());
        userMapper.updateById(update);
        log.info("官方账号启停, userId={}, status={}, operatorId={}", userId, normalized,
                currentUserProvider.currentUserIdOrSystem());
        return AdminOfficialAccountResponse.from(userMapper.selectById(userId));
    }

    private UserEntity requireOfficialAccount(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || TYPE_NORMAL.equals(user.getAccountType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "官方账号不存在");
        }
        if (!campusId().equals(user.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "官方账号不存在");
        }
        return user;
    }

    private String requireAccountType(String value) {
        String normalized = normalize(value);
        if (normalized == null || !ACCOUNT_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "账号类型仅支持 OFFICIAL_AUTO/OFFICIAL_HUMAN/AD_SERVICE");
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "ENABLED";
        }
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "账号状态仅支持 ENABLED/DISABLED");
        }
        return normalized;
    }

    private String normalizeVerification(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "PENDING";
        }
        if (!VERIFICATION_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "认证状态仅支持 NONE/PENDING/APPROVED/REJECTED/DISABLED");
        }
        return normalized;
    }

    private String resolveOfficialTitle(String raw, String displayName, boolean official) {
        if (!official) {
            return null;
        }
        String title = trimmed(raw);
        return title == null ? displayName : title;
    }

    private boolean isOfficialAccountType(String accountType) {
        return TYPE_OFFICIAL_AUTO.equals(accountType) || TYPE_OFFICIAL_HUMAN.equals(accountType);
    }

    private Long campusId() {
        return campusContextService.currentCampusId();
    }

    private String generateUsername() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return "official_" + HexFormat.of().formatHex(bytes);
    }

    private static String trimmed(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 枚举类字段统一转大写，中文/展示字段不要走这里 */
    private static String normalize(String value) {
        String trimmed = trimmed(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }
}
