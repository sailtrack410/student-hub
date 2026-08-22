package com.studenthub.modules.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.identity.entity.RoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

    @Select("SELECT * FROM sys_role WHERE role_code = #{roleCode} AND status = 'ENABLED' AND deleted = 0 LIMIT 1")
    RoleEntity findByRoleCode(@Param("roleCode") String roleCode);
}
