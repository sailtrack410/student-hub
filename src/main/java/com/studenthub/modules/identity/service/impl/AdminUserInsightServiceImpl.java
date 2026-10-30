package com.studenthub.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.privacy.PhoneMasker;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.device.entity.UserDeviceSessionEntity;
import com.studenthub.modules.device.mapper.UserDeviceSessionMapper;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.controller.dto.AdminSsoStatusResponse;
import com.studenthub.modules.identity.controller.dto.AdminUserDetailResponse;
import com.studenthub.modules.identity.controller.dto.AdminUserDeviceResponse;
import com.studenthub.modules.identity.entity.CampusEntity;
import com.studenthub.modules.identity.entity.OrganizationEntity;
import com.studenthub.modules.identity.entity.RoleEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.entity.UserRoleEntity;
import com.studenthub.modules.identity.mapper.CampusMapper;
import com.studenthub.modules.identity.mapper.OrganizationMapper;
import com.studenthub.modules.identity.mapper.RoleMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.mapper.UserRoleMapper;
import com.studenthub.modules.identity.security.SessionStore;
import com.studenthub.modules.identity.service.AdminUserInsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端用户运营信息实现：详情、登录设备、SSO/花名册状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserInsightServiceImpl implements AdminUserInsightService {

    private static final List<String> SSO_ENV_KEYS = List.of(
            "STUDENTHUB_SSO_BASE_URL",
            "STUDENTHUB_SSO_CLIENT_ID",
            "STUDENTHUB_ROSTER_URL");

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final OrganizationMapper organizationMapper;
    private final UserRoleMapper userRoleMapper;
    private final CampusMapper campusMapper;
    private final UserDeviceSessionMapper deviceSessionMapper;
    private final SessionStore sessionStore;
    private final AdminCampusContextService campusContextService;
    private final Environment environment;

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse detail(Long userId) {
        UserEntity user = requireUser(userId);
        CampusEntity campus = user.getCampusId() == null ? null : campusMapper.selectById(user.getCampusId());
        OrganizationEntity defaultOrganization = user.getDefaultOrganizationId() == null
                ? null : organizationMapper.selectById(user.getDefaultOrganizationId());

        List<UserRoleEntity> bindings = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, user.getId()));
        Map<Long, RoleEntity> roleMap = bindings.isEmpty() ? Map.of()
                : roleMapper.selectBatchIds(bindings.stream().map(UserRoleEntity::getRoleId).distinct().toList())
                .stream().collect(Collectors.toMap(RoleEntity::getId, Function.identity()));
        Map<Long, OrganizationEntity> organizationMap = bindings.stream()
                .map(UserRoleEntity::getOrganizationId).filter(Objects::nonNull).distinct().toList().isEmpty()
                ? Map.of()
                : organizationMapper.selectBatchIds(bindings.stream().map(UserRoleEntity::getOrganizationId)
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(OrganizationEntity::getId, Function.identity()));
        List<AdminUserDetailResponse.RoleBinding> roles = bindings.stream()
                .filter(binding -> roleMap.containsKey(binding.getRoleId()))
                .map(binding -> {
                    RoleEntity role = roleMap.get(binding.getRoleId());
                    OrganizationEntity organization = binding.getOrganizationId() == null
                            ? null : organizationMap.get(binding.getOrganizationId());
                    return new AdminUserDetailResponse.RoleBinding(role.getId(), role.getRoleCode(),
                            role.getRoleName(), binding.getOrganizationId(),
                            organization == null ? null : organization.getOrganizationName(),
                            binding.getDataScope());
                })
                .toList();

        long deviceCount = deviceSessionMapper.selectCount(new LambdaQueryWrapper<UserDeviceSessionEntity>()
                .eq(UserDeviceSessionEntity::getUserId, user.getId()));

        return new AdminUserDetailResponse(
                user.getId(), user.getUsername(), user.getDisplayName(), PhoneMasker.mask(user.getPhone()),
                user.getEmail(), user.getStudentNo(), user.getCollege(), user.getGender(), user.getAvatarUrl(),
                user.getCampusId(), campus == null ? null : campus.getCampusName(),
                user.getDefaultOrganizationId(),
                defaultOrganization == null ? null : defaultOrganization.getOrganizationName(),
                user.getIdentityType(), user.getAuthorizationStatus(), user.getStatus(),
                user.getAccountType() == null ? "NORMAL" : user.getAccountType(),
                Boolean.TRUE.equals(user.getOfficial()), user.getOfficialTitle(),
                user.getAccountOperator(), user.getAccountDescription(),
                user.getVerificationStatus() == null ? "NONE" : user.getVerificationStatus(),
                user.getLastLoginAt(), user.getCreatedAt(), roles, (int) deviceCount);
    }

    @Override
    public List<AdminUserDeviceResponse> devices(Long userId) {
        requireUser(userId);
        return deviceSessionMapper.selectList(new LambdaQueryWrapper<UserDeviceSessionEntity>()
                        .eq(UserDeviceSessionEntity::getUserId, userId)
                        .orderByDesc(UserDeviceSessionEntity::getLastActiveAt)
                        .orderByDesc(UserDeviceSessionEntity::getId))
                .stream()
                .map(row -> new AdminUserDeviceResponse(row.getDeviceId(), row.getDeviceName(),
                        row.getOsType(), row.getOsVersion(), row.getIpAddress(), row.getClientType(),
                        sessionStore.existsSessionByHash(row.getTokenHash()) ? "ONLINE" : "OFFLINE",
                        row.getLastActiveAt(), row.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int revokeDevice(Long operatorId, Long userId, String deviceId) {
        requireUser(userId);
        if (!StringUtils.hasText(deviceId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "设备 ID 不能为空");
        }
        UserDeviceSessionEntity row = deviceSessionMapper.selectOne(
                new LambdaQueryWrapper<UserDeviceSessionEntity>()
                        .eq(UserDeviceSessionEntity::getUserId, userId)
                        .eq(UserDeviceSessionEntity::getDeviceId, deviceId));
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在或已被移除");
        }
        boolean online = sessionStore.existsSessionByHash(row.getTokenHash());
        sessionStore.deleteSessionByHash(row.getTokenHash());
        deviceSessionMapper.deleteById(row.getId());
        log.info("管理端已下线设备, userId={}, deviceId={}, online={}, operatorId={}",
                userId, deviceId, online, operatorId);
        return online ? 1 : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int revokeAllDevices(Long operatorId, Long userId) {
        requireUser(userId);
        if (operatorId != null && operatorId.equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "不能在自己的设备列表中执行全部下线，请使用退出登录");
        }
        List<UserDeviceSessionEntity> rows = deviceSessionMapper.selectList(
                new LambdaQueryWrapper<UserDeviceSessionEntity>()
                        .eq(UserDeviceSessionEntity::getUserId, userId));
        int revoked = 0;
        for (UserDeviceSessionEntity row : rows) {
            if (sessionStore.existsSessionByHash(row.getTokenHash())) {
                revoked++;
            }
            sessionStore.deleteSessionByHash(row.getTokenHash());
            deviceSessionMapper.deleteById(row.getId());
        }
        log.info("管理端已下线用户全部设备, userId={}, 设备数={}, 在线会话={}, operatorId={}",
                userId, rows.size(), revoked, operatorId);
        return revoked;
    }

    @Override
    public AdminSsoStatusResponse ssoStatus() {
        boolean configured = SSO_ENV_KEYS.stream()
                .map(environment::getProperty)
                .anyMatch(StringUtils::hasText);
        String status = configured ? "CONFIGURED_PENDING_INTEGRATION" : "NOT_CONFIGURED";
        String message = configured
                ? "已检测到学校 SSO/花名册接入地址配置；对接实现尚未完成，请仍使用 CSV 导入或账号授权流程。"
                : "学校尚未提供统一身份认证/花名册接口，当前未接入；请由学校信息中心提供接口与字段映射后再实现，"
                + "现阶段请使用 CSV 导入模板或账号授权流程。";
        return new AdminSsoStatusResponse(status, configured, false,
                "学校统一身份认证（SSO） / 学工花名册", SSO_ENV_KEYS, message, LocalDateTime.now());
    }

    private UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        Long contextCampusId = campusContextService.currentCampusId();
        if (user.getCampusId() != null && !user.getCampusId().equals(contextCampusId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在或不属于当前校区");
        }
        return user;
    }
}
