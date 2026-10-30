package com.studenthub.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.controller.dto.AdminRoleResponse;
import com.studenthub.modules.identity.controller.dto.AdminUserPageQuery;
import com.studenthub.modules.identity.controller.dto.AdminUserSummaryResponse;
import com.studenthub.modules.identity.controller.dto.WorkerCandidateResponse;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.controller.dto.AdminUserRoleBindingsRequest;
import com.studenthub.modules.identity.entity.OrganizationEntity;
import com.studenthub.modules.identity.entity.RoleEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.entity.UserRoleEntity;
import com.studenthub.modules.identity.mapper.OrganizationMapper;
import com.studenthub.modules.identity.mapper.RoleMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.mapper.UserRoleMapper;
import com.studenthub.modules.identity.mapper.WorkerCandidateRow;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.AuthenticatedUserFactory;
import com.studenthub.modules.identity.security.SessionStore;
import com.studenthub.modules.identity.service.AdminUserService;
import com.studenthub.modules.identity.service.IdentityReviewService;
import com.studenthub.modules.adminscope.security.AdminDataScope;
import com.studenthub.modules.adminscope.security.AdminDataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final Set<String> ALLOWED_STATUS = Set.of("ENABLED", "DISABLED");
    private static final Set<String> ALLOWED_ACTIONS = Set.of("APPROVE", "REJECT");

    /** 视为服务侧角色的角色编码，与用户身份联动 */
    private static final Set<String> STAFF_ROLE_CODES = Set.of("WORKER", "DISPATCHER");

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final OrganizationMapper organizationMapper;
    private final UserRoleMapper userRoleMapper;
    private final SessionStore sessionStore;
    private final AuthenticatedUserFactory authenticatedUserFactory;
    private final AdminCampusContextService campusContextService;
    private final AdminDataScopeService adminDataScopeService;
    private final IdentityReviewService identityReviewService;

    @Override
    public PageResult<AdminUserSummaryResponse> page(AdminUserPageQuery query) {
        List<Long> roleUserIds = null;
        if (query.roleId() != null) {
            roleUserIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                            .eq(UserRoleEntity::getRoleId, query.roleId()))
                    .stream().map(UserRoleEntity::getUserId).distinct().toList();
            if (roleUserIds.isEmpty()) {
                return PageResult.of(query.pageNumOrDefault(), query.pageSizeOrDefault(), 0, List.of());
            }
        }

        Long contextCampusId = campusContextService.currentCampusId();
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getCampusId, contextCampusId)
                .and(StringUtils.hasText(query.keyword()), w -> {
                    String keyword = query.keyword().trim();
                    w.like(UserEntity::getUsername, keyword)
                            .or().like(UserEntity::getDisplayName, keyword)
                            .or().like(UserEntity::getPhone, keyword);
                })
                .eq(StringUtils.hasText(query.status()), UserEntity::getStatus, query.status())
                .eq(StringUtils.hasText(query.identityType()), UserEntity::getIdentityType, query.identityType())
                .eq(StringUtils.hasText(query.authorizationStatus()), UserEntity::getAuthorizationStatus,
                        query.authorizationStatus())
                .eq(query.official() != null, UserEntity::getOfficial, query.official())
                .in(roleUserIds != null, UserEntity::getId, roleUserIds)
                .orderByDesc(UserEntity::getOfficial)
                .orderByDesc(UserEntity::getId);

        AdminDataScope dataScope = adminDataScopeService.current();
        if (!dataScope.unrestricted()) {
            if (dataScope.selfOnly()) {
                wrapper.eq(UserEntity::getId, dataScope.userId());
            } else if (dataScope.hasOrganizationLimit()) {
                wrapper.in(UserEntity::getDefaultOrganizationId, dataScope.organizationIds());
            }
        }
        Page<UserEntity> page = userMapper.selectPage(
                new Page<>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
        Map<Long, List<AdminUserSummaryResponse.RoleItem>> rolesByUser = loadRoles(page.getRecords());
        List<AdminUserSummaryResponse> records = page.getRecords().stream()
                .map(user -> AdminUserSummaryResponse.from(user,
                        rolesByUser.getOrDefault(user.getId(), List.of())))
                .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long operatorId, Long userId, String status) {
        String normalized = status == null ? "" : status.trim();
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "状态仅支持 ENABLED/DISABLED");
        }
        UserEntity user = requireUser(userId);
        requireNotSelf(operatorId, userId, "不能停用/启用自己的账号");
        if (normalized.equals(user.getStatus())) {
            return;
        }
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setStatus(normalized);
        update.setVersion(user.getVersion());
        userMapper.updateById(update);

        if ("DISABLED".equals(normalized)) {
            int kicked = sessionStore.deleteUserSessions(userId);
            log.info("账号已停用并下线会话, userId={}, operatorId={}, 踢出会话数={}", userId, operatorId, kicked);
        } else {
            refreshUserSessions(userId);
            log.info("账号已启用, userId={}, operatorId={}", userId, operatorId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewAuthorization(Long operatorId, Long userId, String action, String reasonCode, String reason) {
        String normalized = action == null ? "" : action.trim();
        if (!ALLOWED_ACTIONS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "审核动作仅支持 APPROVE/REJECT");
        }
        UserEntity user = requireUser(userId);
        String targetStatus = "APPROVE".equals(normalized) ? "AUTHORIZED" : "REJECTED";
        if (targetStatus.equals(user.getAuthorizationStatus())) {
            return;
        }
        // U04：拒绝必须带模板或自定义原因；解析结果同时写入用户备注与审核记录。
        String resolvedReason = identityReviewService.resolveReason(normalized, reasonCode, reason);
        LocalDateTime now = LocalDateTime.now();
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setAuthorizationStatus(targetStatus);
        update.setAuthorizationNote(resolvedReason);
        update.setAuthorizationReviewedBy(operatorId);
        update.setAuthorizationReviewedAt(now);
        if (user.getAuthorizationSubmittedAt() == null) {
            update.setAuthorizationSubmittedAt(now);
        }
        update.setVersion(user.getVersion());
        userMapper.updateById(update);

        refreshUserSessions(userId);
        identityReviewService.recordDecision(operatorId, user, user.getAuthorizationStatus(), targetStatus,
                reasonCode, resolvedReason);
        log.info("授权审核完成, userId={}, action={}, operatorId={}, reasonCode={}",
                userId, normalized, operatorId, reasonCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserSummaryResponse updateRoles(Long operatorId, Long userId, List<Long> roleIds) {
        requireNotSelf(operatorId, userId, "不能调整自己的角色");
        UserEntity user = requireUser(userId);

        List<Long> distinctRoleIds = roleIds.stream().distinct().toList();
        Map<Long, RoleEntity> roleMap = distinctRoleIds.isEmpty() ? Map.of()
                : roleMapper.selectBatchIds(distinctRoleIds).stream()
                        .collect(Collectors.toMap(RoleEntity::getId, Function.identity()));
        for (Long roleId : distinctRoleIds) {
            RoleEntity role = roleMap.get(roleId);
            if (role == null || !"ENABLED".equals(role.getStatus())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "角色不存在或已停用: " + roleId);
            }
        }

        List<UserRoleEntity> existing = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId));
        Set<Long> existingRoleIds = existing.stream().map(UserRoleEntity::getRoleId).collect(Collectors.toSet());
        List<Long> toRemove = existingRoleIds.stream().filter(id -> !distinctRoleIds.contains(id)).toList();
        List<Long> toAdd = distinctRoleIds.stream().filter(id -> !existingRoleIds.contains(id)).toList();
        if (toRemove.isEmpty() && toAdd.isEmpty()) {
            return summaryWithRoles(user);
        }

        if (!toRemove.isEmpty()) {
            userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>()
                    .eq(UserRoleEntity::getUserId, userId)
                    .in(UserRoleEntity::getRoleId, toRemove));
        }
        for (Long roleId : toAdd) {
            UserRoleEntity binding = new UserRoleEntity();
            binding.setUserId(userId);
            binding.setRoleId(roleId);
            binding.setOrganizationId(user.getDefaultOrganizationId());
            binding.setDataScope(switch (roleMap.get(roleId).getRoleCode()) {
                case "PLATFORM_ADMIN" -> "ALL";
                case "DISPATCHER" -> "ORGANIZATION_AND_CHILDREN";
                case "WORKER" -> "ASSIGNED";
                default -> "SELF";
            });
            binding.setCreatedAt(java.time.LocalDateTime.now());
            binding.setCreatedBy(operatorId);
            userRoleMapper.insert(binding);
        }

        applyIdentityFallback(user, distinctRoleIds);
        refreshUserSessions(userId);
        log.info("用户角色已调整, userId={}, roleIds={}, operatorId={}", userId, distinctRoleIds, operatorId);
        return summaryWithRoles(userMapper.selectById(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserSummaryResponse updateRoleBindings(Long operatorId, Long userId,
                                                       List<AdminUserRoleBindingsRequest.Binding> bindings) {
        requireNotSelf(operatorId, userId, "不能调整自己的角色");
        UserEntity user = requireUser(userId);
        List<AdminUserRoleBindingsRequest.Binding> list = bindings == null ? List.of() : bindings;
        List<Long> roleIds = list.stream().map(AdminUserRoleBindingsRequest.Binding::roleId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, RoleEntity> roleMap = roleIds.isEmpty() ? Map.of()
                : roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(RoleEntity::getId, Function.identity()));
        for (Long roleId : roleIds) {
            RoleEntity role = roleMap.get(roleId);
            if (role == null || !"ENABLED".equals(role.getStatus())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "角色不存在或已停用: " + roleId);
            }
        }
        Set<String> allowedScopes = Set.of("SELF", "ASSIGNED", "ORGANIZATION",
                "ORGANIZATION_AND_CHILDREN", "ALL");
        for (AdminUserRoleBindingsRequest.Binding binding : list) {
            if (binding.roleId() == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "角色不能为空");
            }
            if (binding.organizationId() != null) {
                OrganizationEntity organization = organizationMapper.selectById(binding.organizationId());
                if (organization == null) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "组织不存在: " + binding.organizationId());
                }
            }
            String scope = binding.dataScope() == null ? "" : binding.dataScope().trim().toUpperCase();
            if (!allowedScopes.contains(scope)) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "数据范围不合法: " + binding.dataScope());
            }
        }
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (AdminUserRoleBindingsRequest.Binding binding : list) {
            Long roleId = binding.roleId();
            if (roleId == null) continue;
            UserRoleEntity entity = new UserRoleEntity();
            entity.setUserId(userId);
            entity.setRoleId(roleId);
            entity.setOrganizationId(binding.organizationId() == null
                    ? user.getDefaultOrganizationId() : binding.organizationId());
            entity.setDataScope(binding.dataScope() == null
                    ? "SELF" : binding.dataScope().trim().toUpperCase());
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            userRoleMapper.insert(entity);
        }
        applyIdentityFallback(user, roleIds);
        refreshUserSessions(userId);
        log.info("用户角色绑定已调整, userId={}, bindings={}, operatorId={}", userId, list.size(), operatorId);
        return summaryWithRoles(userMapper.selectById(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserSummaryResponse updateOfficial(Long operatorId, Long userId, Boolean official, String officialTitle) {
        UserEntity user = requireUser(userId);
        boolean target = Boolean.TRUE.equals(official);
        String title = officialTitle == null ? null : officialTitle.trim();
        if (target && (title == null || title.isEmpty())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "设为官方账号时必须填写官方头衔");
        }
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setOfficial(target);
        update.setOfficialTitle(target ? title : null);
        // V66 账号类型与 official 标记联动：取消官方回落 NORMAL，设为官方时默认人工运营号；
        // 若账号已经是自动号/广告服务号则保留其类型，避免管理端旧入口破坏新账号体系。
        String currentType = user.getAccountType();
        String targetType;
        if (!target) {
            targetType = "NORMAL";
        } else if (currentType != null && !"NORMAL".equals(currentType)) {
            targetType = currentType;
        } else {
            targetType = "OFFICIAL_HUMAN";
        }
        update.setAccountType(targetType);
        update.setVerificationStatus(target ? "APPROVED" : "NONE");
        update.setVersion(user.getVersion());
        userMapper.updateById(update);
        refreshUserSessions(userId);
        log.info("官方账号标识已更新, userId={}, official={}, title={}, operatorId={}",
                userId, target, title, operatorId);
        return summaryWithRoles(userMapper.selectById(userId));
    }

    @Override
    public List<WorkerCandidateResponse> workerCandidates(String keyword) {
        List<WorkerCandidateRow> rows = userMapper.findEnabledWorkers(campusContextService.currentCampusId());
        if (!StringUtils.hasText(keyword)) {
            return rows.stream().map(this::toCandidate).toList();
        }
        String needle = keyword.trim();
        return rows.stream()
                .filter(row -> containsIgnoreCase(row.getUsername(), needle)
                        || containsIgnoreCase(row.getDisplayName(), needle)
                        || containsIgnoreCase(row.getPhone(), needle))
                .map(this::toCandidate)
                .toList();
    }

    @Override
    public List<AdminRoleResponse> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                        .eq(RoleEntity::getStatus, "ENABLED")
                        .orderByAsc(RoleEntity::getId))
                .stream().map(AdminRoleResponse::from).toList();
    }

    /**
     * 角色变动后的身份联动：拥有服务角色则升为 LOGISTICS/AUTHORIZED；
     * 失去全部服务角色且当前正是联动所得值时回落为 STUDENT/BASIC。
     */
    private void applyIdentityFallback(UserEntity user, List<Long> roleIds) {
        boolean hasStaffRole = roleIds.stream()
                .anyMatch(roleId -> STAFF_ROLE_CODES.contains(roleMapper.selectById(roleId).getRoleCode()));
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        if (hasStaffRole) {
            if (!"ADMIN".equals(user.getIdentityType())) {
                update.setIdentityType("LOGISTICS");
            }
            update.setAuthorizationStatus("AUTHORIZED");
        } else if ("LOGISTICS".equals(user.getIdentityType())
                || "AUTHORIZED".equals(user.getAuthorizationStatus())) {
            if ("LOGISTICS".equals(user.getIdentityType())) {
                update.setIdentityType("STUDENT");
            }
            if ("AUTHORIZED".equals(user.getAuthorizationStatus())) {
                update.setAuthorizationStatus("BASIC");
            }
        } else {
            return;
        }
        update.setVersion(user.getVersion());
        userMapper.updateById(update);
    }

    private void refreshUserSessions(Long userId) {
        UserEntity latest = userMapper.selectById(userId);
        if (latest == null) {
            return;
        }
        AuthenticatedUser refreshed = authenticatedUserFactory.build(latest);
        int count = sessionStore.refreshUserSessions(userId, refreshed);
        if (count > 0) {
            log.info("用户在线会话已原地刷新, userId={}, 会话数={}", userId, count);
        }
    }

    private Map<Long, List<AdminUserSummaryResponse.RoleItem>> loadRoles(List<UserEntity> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<Long> userIds = users.stream().map(UserEntity::getId).toList();
        List<UserRoleEntity> bindings = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                .in(UserRoleEntity::getUserId, userIds));
        if (bindings.isEmpty()) {
            return Map.of();
        }
        Map<Long, RoleEntity> roleMap = roleMapper.selectBatchIds(
                        bindings.stream().map(UserRoleEntity::getRoleId).distinct().toList()).stream()
                .collect(Collectors.toMap(RoleEntity::getId, Function.identity()));
        List<Long> organizationIds = bindings.stream().map(UserRoleEntity::getOrganizationId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, OrganizationEntity> orgMap = organizationIds.isEmpty() ? Map.of()
                : organizationMapper.selectBatchIds(organizationIds).stream()
                .collect(Collectors.toMap(OrganizationEntity::getId, Function.identity()));
        return bindings.stream()
                .filter(binding -> roleMap.containsKey(binding.getRoleId()))
                .collect(Collectors.groupingBy(UserRoleEntity::getUserId,
                        Collectors.mapping(binding -> {
                            RoleEntity role = roleMap.get(binding.getRoleId());
                            OrganizationEntity organization = binding.getOrganizationId() == null
                                    ? null : orgMap.get(binding.getOrganizationId());
                            return new AdminUserSummaryResponse.RoleItem(role.getId(), role.getRoleCode(),
                                    role.getRoleName(), binding.getOrganizationId(),
                                    organization == null ? null : organization.getOrganizationName(),
                                    binding.getDataScope());
                        }, Collectors.toList())));
    }

    private AdminUserSummaryResponse summaryWithRoles(UserEntity user) {
        return AdminUserSummaryResponse.from(user, loadRoles(List.of(user))
                .getOrDefault(user.getId(), List.of()));
    }

    private WorkerCandidateResponse toCandidate(WorkerCandidateRow row) {
        return WorkerCandidateResponse.from(row);
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle.toLowerCase());
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

    private void requireNotSelf(Long operatorId, Long userId, String message) {
        if (operatorId != null && operatorId.equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, message);
        }
    }
}
