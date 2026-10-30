package com.studenthub.modules.adminscope.security;

import java.util.List;

/**
 * 管理端数据范围。unrestricted=true 表示平台级/ALL，不做组织过滤。
 * selfOnly/assignedOnly 用于没有组织范围的列表（例如个人申请、被指派工单）。
 */
public record AdminDataScope(
        boolean unrestricted,
        boolean selfOnly,
        boolean assignedOnly,
        Long userId,
        List<Long> organizationIds) {

    public boolean hasOrganizationLimit() {
        return !unrestricted && organizationIds != null && !organizationIds.isEmpty();
    }

    public boolean allowsOrganization(Long organizationId) {
        if (unrestricted) {
            return true;
        }
        if (organizationId == null || organizationIds == null) {
            return false;
        }
        return organizationIds.contains(organizationId);
    }
}
