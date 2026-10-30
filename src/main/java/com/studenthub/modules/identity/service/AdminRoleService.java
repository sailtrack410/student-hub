package com.studenthub.modules.identity.service;

import com.studenthub.modules.identity.controller.dto.AdminPermissionResponse;
import com.studenthub.modules.identity.controller.dto.AdminRoleCopyRequest;
import com.studenthub.modules.identity.controller.dto.AdminRoleCreateRequest;
import com.studenthub.modules.identity.controller.dto.AdminRoleDetailResponse;
import com.studenthub.modules.identity.controller.dto.AdminRoleUpdateRequest;

import java.util.List;

public interface AdminRoleService {

    List<AdminPermissionResponse> listPermissions();

    AdminRoleDetailResponse detail(Long roleId);

    AdminRoleDetailResponse updateRole(Long operatorId, Long roleId, AdminRoleUpdateRequest request);

    AdminRoleDetailResponse updatePermissions(Long operatorId, Long roleId, List<Long> permissionIds);

    /**
     * 新建角色（U02）；角色编码唯一，初始权限可选。
     */
    AdminRoleDetailResponse createRole(Long operatorId, AdminRoleCreateRequest request);

    /**
     * 删除角色（软删）：内置角色（PLATFORM_ADMIN/REQUESTER/WORKER/DISPATCHER）与仍被用户引用的角色拒绝删除。
     */
    void deleteRole(Long operatorId, Long roleId);

    /**
     * 以现有角色为模板复制权限集合生成新角色（U02）。
     */
    AdminRoleDetailResponse copyRole(Long operatorId, Long roleId, AdminRoleCopyRequest request);
}
