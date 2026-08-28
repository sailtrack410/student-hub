package com.studenthub.modules.workorder.service.impl;

import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.DataScope;
import com.studenthub.modules.identity.security.RoleScope;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 调度端数据范围解析：ALL 放开全部；ORGANIZATION_AND_CHILDREN / CAMPUS / ORGANIZATION
 * 按角色绑定组织展开；SELF / ASSIGNED 不产生队列可见范围。
 */
@Component
public class DispatcherScopeResolver {

    private final WorkOrderMapper workOrderMapper;

    public DispatcherScopeResolver(WorkOrderMapper workOrderMapper) {
        this.workOrderMapper = workOrderMapper;
    }

    /**
     * @return null 表示不限制组织范围；空列表表示无任何可见范围。
     */
    public List<Long> resolveVisibleOrganizationIds(AuthenticatedUser operator) {
        List<RoleScope> scopes = operator.roleScopes() == null ? List.of() : operator.roleScopes();
        for (RoleScope scope : scopes) {
            if (scope.dataScope() == DataScope.ALL) {
                return null;
            }
        }
        List<Long> visible = new ArrayList<>();
        for (RoleScope scope : scopes) {
            if (scope.organizationId() == null) {
                continue;
            }
            switch (scope.dataScope()) {
                case ORGANIZATION_AND_CHILDREN, CAMPUS ->
                        visible.addAll(workOrderMapper.selectSelfAndDescendantOrgIds(scope.organizationId()));
                case ORGANIZATION -> visible.add(scope.organizationId());
                default -> {
                    // SELF / ASSIGNED 不扩展队列范围
                }
            }
        }
        return visible.stream().distinct().toList();
    }
}
