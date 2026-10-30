package com.studenthub.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.controller.dto.AdminPermissionResponse;
import com.studenthub.modules.identity.controller.dto.AdminRoleCopyRequest;
import com.studenthub.modules.identity.controller.dto.AdminRoleCreateRequest;
import com.studenthub.modules.identity.controller.dto.AdminRoleDetailResponse;
import com.studenthub.modules.identity.controller.dto.AdminRoleUpdateRequest;
import com.studenthub.modules.identity.entity.PermissionEntity;
import com.studenthub.modules.identity.entity.RoleEntity;
import com.studenthub.modules.identity.entity.RolePermissionEntity;
import com.studenthub.modules.identity.entity.UserRoleEntity;
import com.studenthub.modules.identity.mapper.PermissionMapper;
import com.studenthub.modules.identity.mapper.RoleMapper;
import com.studenthub.modules.identity.mapper.RolePermissionMapper;
import com.studenthub.modules.identity.mapper.UserRoleMapper;
import com.studenthub.modules.identity.service.AdminRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl implements AdminRoleService {

    /** 内置角色：由迁移脚本初始化、被权限模型与业务逻辑引用，不允许删除 */
    private static final Set<String> BUILT_IN_ROLE_CODES = Set.of(
            "PLATFORM_ADMIN", "REQUESTER", "WORKER", "DISPATCHER");
    private static final Set<String> ALLOWED_ROLE_TYPES = Set.of(
            "ADMIN", "REQUESTER", "WORKER", "DISPATCHER");

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public List<AdminPermissionResponse> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<PermissionEntity>()
                        .eq(PermissionEntity::getStatus, "ENABLED")
                        .orderByAsc(PermissionEntity::getPermissionCode))
                .stream().map(AdminPermissionResponse::from).toList();
    }

    @Override
    public AdminRoleDetailResponse detail(Long roleId) {
        RoleEntity role = requireRole(roleId);
        List<Long> permissionIds = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<RolePermissionEntity>()
                                .eq(RolePermissionEntity::getRoleId, roleId))
                .stream().map(RolePermissionEntity::getPermissionId).distinct().toList();
        Map<Long, PermissionEntity> permissions = permissionIds.isEmpty() ? Map.of()
                : permissionMapper.selectBatchIds(permissionIds).stream()
                .collect(Collectors.toMap(PermissionEntity::getId, Function.identity()));
        List<String> codes = permissionIds.stream()
                .map(permissions::get)
                .filter(java.util.Objects::nonNull)
                .map(PermissionEntity::getPermissionCode)
                .sorted()
                .toList();
        return new AdminRoleDetailResponse(role.getId(), role.getRoleCode(), role.getRoleName(),
                role.getRoleType(), role.getStatus(), codes, new ArrayList<>(permissionIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminRoleDetailResponse updateRole(Long operatorId, Long roleId, AdminRoleUpdateRequest request) {
        RoleEntity role = requireRole(roleId);
        String status = request.status().trim().toUpperCase();
        if (!status.equals("ENABLED") && !status.equals("DISABLED")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "角色状态仅支持 ENABLED/DISABLED");
        }
        if ("PLATFORM_ADMIN".equals(role.getRoleCode()) && "DISABLED".equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "平台管理员角色不允许停用");
        }
        RoleEntity update = new RoleEntity();
        update.setId(roleId);
        update.setRoleName(request.roleName().trim());
        update.setRoleType(request.roleType().trim().toUpperCase());
        update.setStatus(status);
        update.setUpdatedBy(operatorId);
        update.setUpdatedAt(LocalDateTime.now());
        update.setVersion(role.getVersion());
        roleMapper.updateById(update);
        log.info("角色已更新, roleId={}, operatorId={}", roleId, operatorId);
        return detail(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminRoleDetailResponse updatePermissions(Long operatorId, Long roleId, List<Long> permissionIds) {
        requireRole(roleId);
        List<Long> distinct = permissionIds == null ? List.of()
                : permissionIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (!distinct.isEmpty()) {
            Map<Long, PermissionEntity> found = permissionMapper.selectBatchIds(distinct).stream()
                    .collect(Collectors.toMap(PermissionEntity::getId, Function.identity()));
            for (Long id : distinct) {
                PermissionEntity p = found.get(id);
                if (p == null || !"ENABLED".equals(p.getStatus())) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "权限不存在或已停用: " + id);
                }
            }
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionEntity>()
                .eq(RolePermissionEntity::getRoleId, roleId));
        LocalDateTime now = LocalDateTime.now();
        for (Long permissionId : distinct) {
            RolePermissionEntity binding = new RolePermissionEntity();
            binding.setRoleId(roleId);
            binding.setPermissionId(permissionId);
            binding.setCreatedAt(now);
            binding.setCreatedBy(operatorId);
            rolePermissionMapper.insert(binding);
        }
        log.info("角色权限已更新, roleId={}, permissionCount={}, operatorId={}",
                roleId, distinct.size(), operatorId);
        return detail(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminRoleDetailResponse createRole(Long operatorId, AdminRoleCreateRequest request) {
        String roleCode = request.roleCode() == null ? "" : request.roleCode().trim().toUpperCase(Locale.ROOT);
        String roleName = request.roleName() == null ? "" : request.roleName().trim();
        String roleType = normalizeRoleType(request.roleType());
        String status = normalizeStatus(request.status(), "ENABLED");
        if (roleCode.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "角色编码不能为空");
        }
        Long duplicate = roleMapper.selectCount(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, roleCode));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "角色编码已存在: " + roleCode);
        }
        List<Long> permissionIds = normalizePermissionIds(request.permissionIds());

        RoleEntity role = new RoleEntity();
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setRoleType(roleType);
        role.setStatus(status);
        role.setCreatedBy(operatorId);
        role.setUpdatedBy(operatorId);
        roleMapper.insert(role);
        replacePermissions(operatorId, role.getId(), permissionIds);
        log.info("角色已创建, roleId={}, roleCode={}, permissions={}, operatorId={}",
                role.getId(), roleCode, permissionIds.size(), operatorId);
        return detail(role.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long operatorId, Long roleId) {
        RoleEntity role = requireRole(roleId);
        if (BUILT_IN_ROLE_CODES.contains(role.getRoleCode())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "内置角色不允许删除: " + role.getRoleCode() + "（如需停用请改为 DISABLED）");
        }
        Long inUse = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getRoleId, roleId));
        if (inUse != null && inUse > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "该角色仍被 " + inUse + " 个用户使用，请先解除用户角色绑定");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionEntity>()
                .eq(RolePermissionEntity::getRoleId, roleId));
        roleMapper.deleteById(roleId);
        log.info("角色已删除, roleId={}, roleCode={}, operatorId={}", roleId, role.getRoleCode(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminRoleDetailResponse copyRole(Long operatorId, Long roleId, AdminRoleCopyRequest request) {
        RoleEntity source = requireRole(roleId);
        String roleName = request == null ? null : request.roleName();
        roleName = StringUtils.hasText(roleName) ? roleName.trim() : source.getRoleName() + " 副本";
        String roleCode = request == null ? null : request.roleCode();
        roleCode = StringUtils.hasText(roleCode) ? roleCode.trim().toUpperCase(Locale.ROOT) : null;
        if (roleCode == null) {
            roleCode = autoCopyCode(source.getRoleCode());
        } else if (roleMapper.selectCount(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, roleCode)) > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "角色编码已存在: " + roleCode);
        }

        List<Long> permissionIds = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<RolePermissionEntity>()
                                .eq(RolePermissionEntity::getRoleId, source.getId()))
                .stream().map(RolePermissionEntity::getPermissionId).distinct().toList();

        RoleEntity copy = new RoleEntity();
        copy.setRoleCode(roleCode);
        copy.setRoleName(roleName);
        copy.setRoleType(source.getRoleType());
        copy.setStatus("ENABLED");
        copy.setCreatedBy(operatorId);
        copy.setUpdatedBy(operatorId);
        roleMapper.insert(copy);
        replacePermissions(operatorId, copy.getId(), permissionIds);
        log.info("角色已复制, sourceRoleId={}, newRoleId={}, roleCode={}, permissions={}, operatorId={}",
                roleId, copy.getId(), roleCode, permissionIds.size(), operatorId);
        return detail(copy.getId());
    }

    private void replacePermissions(Long operatorId, Long roleId, List<Long> permissionIds) {
        LocalDateTime now = LocalDateTime.now();
        for (Long permissionId : permissionIds) {
            RolePermissionEntity binding = new RolePermissionEntity();
            binding.setRoleId(roleId);
            binding.setPermissionId(permissionId);
            binding.setCreatedAt(now);
            binding.setCreatedBy(operatorId);
            rolePermissionMapper.insert(binding);
        }
    }

    private List<Long> normalizePermissionIds(List<Long> permissionIds) {
        List<Long> distinct = permissionIds == null ? List.of()
                : permissionIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return distinct;
        }
        Map<Long, PermissionEntity> found = permissionMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(PermissionEntity::getId, Function.identity()));
        for (Long id : distinct) {
            PermissionEntity permission = found.get(id);
            if (permission == null || !"ENABLED".equals(permission.getStatus())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "权限不存在或已停用: " + id);
            }
        }
        return distinct;
    }

    private String normalizeRoleType(String roleType) {
        String value = roleType == null ? "" : roleType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLE_TYPES.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "角色类型仅支持 ADMIN/REQUESTER/WORKER/DISPATCHER");
        }
        return value;
    }

    private String normalizeStatus(String status, String defaultValue) {
        String value = status == null || status.isBlank() ? defaultValue : status.trim().toUpperCase(Locale.ROOT);
        if (!"ENABLED".equals(value) && !"DISABLED".equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "角色状态仅支持 ENABLED/DISABLED");
        }
        return value;
    }

    private String autoCopyCode(String sourceCode) {
        String base = sourceCode + "_COPY";
        String candidate = base;
        int suffix = 2;
        while (roleMapper.selectCount(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, candidate)) > 0) {
            candidate = base + "_" + suffix;
            suffix++;
            if (suffix > 99) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "自动生成角色编码失败，请手动指定");
            }
        }
        return candidate;
    }

    private RoleEntity requireRole(Long roleId) {
        RoleEntity role = roleMapper.selectById(roleId);
        if (role == null || Boolean.TRUE.equals(role.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }
}
