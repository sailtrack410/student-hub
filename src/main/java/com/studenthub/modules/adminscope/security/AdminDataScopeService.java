package com.studenthub.modules.adminscope.security;

import com.studenthub.modules.identity.entity.OrganizationEntity;
import com.studenthub.modules.identity.mapper.OrganizationMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.identity.security.DataScope;
import com.studenthub.modules.identity.security.RoleScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理端数据范围解析。平台管理员/ALL 不受限；其余角色按绑定组织展开，
 * SELF/ASSIGNED 只产生本人/被指派约束。组织树在内存展开，避免各模块重复 SQL。
 */
@Service
@RequiredArgsConstructor
public class AdminDataScopeService {

    private final CurrentUserProvider currentUserProvider;
    private final OrganizationMapper organizationMapper;

    public AdminDataScope current() {
        return resolve(currentUserProvider.requireCurrentUser());
    }

    public AdminDataScope resolve(AuthenticatedUser operator) {
        if (operator == null) {
            return new AdminDataScope(false, true, false, null, List.of());
        }
        List<String> permissions = operator.permissionCodes() == null ? List.of() : operator.permissionCodes();
        if (permissions.contains("platform:all")) {
            return new AdminDataScope(true, false, false, operator.userId(), null);
        }
        List<RoleScope> scopes = operator.roleScopes() == null ? List.of() : operator.roleScopes();
        boolean selfOnly = false;
        boolean assignedOnly = false;
        Set<Long> directOrgIds = new LinkedHashSet<>();
        for (RoleScope scope : scopes) {
            if (scope.dataScope() == DataScope.ALL) {
                return new AdminDataScope(true, false, false, operator.userId(), null);
            }
            if (scope.dataScope() == DataScope.SELF) {
                selfOnly = true;
                continue;
            }
            if (scope.dataScope() == DataScope.ASSIGNED) {
                assignedOnly = true;
                continue;
            }
            if (scope.organizationId() == null) {
                continue;
            }
            if (scope.dataScope() == DataScope.ORGANIZATION) {
                directOrgIds.add(scope.organizationId());
            } else if (scope.dataScope() == DataScope.ORGANIZATION_AND_CHILDREN
                    || scope.dataScope() == DataScope.CAMPUS) {
                directOrgIds.add(scope.organizationId());
            }
        }
        if (directOrgIds.isEmpty()) {
            // 没有组织范围也没有明确的自/被指派范围时，按本人最小权限处理。
            if (!assignedOnly) {
                selfOnly = true;
            }
            return new AdminDataScope(false, selfOnly, assignedOnly, operator.userId(), List.of());
        }
        Set<Long> visible = expandOrganizations(directOrgIds);
        return new AdminDataScope(false, selfOnly, assignedOnly, operator.userId(), List.copyOf(visible));
    }

    private Set<Long> expandOrganizations(Set<Long> rootIds) {
        if (rootIds.isEmpty()) {
            return Set.of();
        }
        List<OrganizationEntity> all = organizationMapper.selectList(null);
        Set<Long> visible = new LinkedHashSet<>(rootIds);
        Deque<Long> queue = new ArrayDeque<>(rootIds);
        Set<Long> visited = new HashSet<>(rootIds);
        while (!queue.isEmpty()) {
            Long parent = queue.poll();
            for (OrganizationEntity organization : all) {
                Long id = organization.getId();
                if (id == null || visited.contains(id)) {
                    continue;
                }
                if (parent.equals(organization.getParentId())) {
                    visited.add(id);
                    visible.add(id);
                    queue.add(id);
                }
            }
        }
        return visible;
    }

    /** 将当前范围转成 MyBatis-Plus in 条件；unrestricted 返回 null。 */
    public List<Long> currentOrganizationIdsOrNull() {
        AdminDataScope scope = current();
        return scope.unrestricted() ? null : new ArrayList<>(scope.organizationIds());
    }
}
