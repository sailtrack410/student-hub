package com.studenthub.modules.servicerequest.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface RequestRecipientMapper {
    /** 先按既有管理权限筛选候选账号，数据范围再由统一范围服务校验。 */
    @Select("""
        SELECT DISTINCT u.id FROM sys_user u
        JOIN sys_user_role ur ON ur.user_id = u.id
        JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 'ENABLED'
        JOIN sys_role_permission rp ON rp.role_id = r.id
        JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0 AND p.status = 'ENABLED'
        WHERE u.deleted = 0 AND u.status = 'ENABLED' AND u.campus_id = #{campusId}
          AND (p.permission_code = 'platform:all' OR p.permission_code = #{permission}
               OR (#{permission} = 'catalog:manage' AND p.permission_code = 'work-order:dispatch'))
        ORDER BY u.id LIMIT 200
        """)
    List<Long> candidates(@Param("campusId") Long campusId, @Param("permission") String permission);
}
