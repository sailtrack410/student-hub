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
import com.studenthub.modules.identity.entity.RoleEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.entity.UserRoleEntity;
import com.studenthub.modules.identity.mapper.RoleMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.mapper.UserRoleMapper;
import com.studenthub.modules.identity.mapper.WorkerCandidateRow;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.AuthenticatedUserFactory;
import com.studenthub.modules.identity.security.SessionStore;
import com.studenthub.modules.identity.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final UserRoleMapper userRoleMapper;
    private final SessionStore sessionStore;
    private final AuthenticatedUserFactory authenticatedUserFactory;

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

        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
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
                .in(roleUserIds != null, UserEntity::getId, roleUserIds)
                .orderByDesc(UserEntity::getId);

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
    public void reviewAuthorization(Long operatorId, Long userId, String action, String reason) {
        String normalized = action == null ? "" : action.trim();
        if (!ALLOWED_ACTIONS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "审核动作仅支持 APPROVE/REJECT");
        }
        UserEntity user = requireUser(userId);
        String targetStatus = "APPROVE".equals(normalized) ? "AUTHORIZED" : "REJECTED";
        if (targetStatus.equals(user.getAuthorizationStatus())) {
            return;
        }
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setAuthorizationStatus(targetStatus);
        update.setVersion(user.getVersion());
        userMapper.updateById(update);

        refreshUserSessions(userId);
        log.info("授权审核完成, userId={}, action={}, operatorId={}, reason={}",
                userId, normalized, operatorId, reason);
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
    public List<WorkerCandidateResponse> workerCandidates(String keyword) {
        List<WorkerCandidateRow> rows = userMapper.findEnabledWorkers();
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
        return bindings.stream()
                .filter(binding -> roleMap.containsKey(binding.getRoleId()))
                .collect(Collectors.groupingBy(UserRoleEntity::getUserId,
                        Collectors.mapping(binding -> {
                            RoleEntity role = roleMap.get(binding.getRoleId());
                            return new AdminUserSummaryResponse.RoleItem(role.getId(), role.getRoleCode(),
                                    role.getRoleName());
                        }, Collectors.toList())));
    }

    private AdminUserSummaryResponse summaryWithRoles(UserEntity user) {
        return AdminUserSummaryResponse.from(user, loadRoles(List.of(user))
                .getOrDefault(user.getId(), List.of()));
    }

    private WorkerCandidateResponse toCandidate(WorkerCandidateRow row) {
        return new WorkerCandidateResponse(row.getUserId(), row.getUsername(), row.getDisplayName(), row.getPhone());
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle.toLowerCase());
    }

    private UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void requireNotSelf(Long operatorId, Long userId, String message) {
        if (operatorId != null && operatorId.equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, message);
        }
    }
}
