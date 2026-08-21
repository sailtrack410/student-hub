package com.studenthub.modules.identity.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IdentityAccessMapper {

    @Select("""
            SELECT DISTINCT r.role_code, ur.organization_id, ur.data_scope
              FROM sys_user_role ur
              JOIN sys_role r ON r.id = ur.role_id
             WHERE ur.user_id = #{userId}
               AND r.status = 'ENABLED'
               AND r.deleted = 0
            """)
    List<RoleScopeRow> findRoleScopes(Long userId);

    @Select("""
            SELECT DISTINCT p.permission_code
              FROM sys_user_role ur
              JOIN sys_role r ON r.id = ur.role_id
              JOIN sys_role_permission rp ON rp.role_id = r.id
              JOIN sys_permission p ON p.id = rp.permission_id
             WHERE ur.user_id = #{userId}
               AND r.status = 'ENABLED'
               AND r.deleted = 0
               AND p.status = 'ENABLED'
               AND p.deleted = 0
             ORDER BY p.permission_code
            """)
    List<String> findPermissionCodes(Long userId);
}
