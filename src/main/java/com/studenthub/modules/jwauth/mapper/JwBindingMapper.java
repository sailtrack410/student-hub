package com.studenthub.modules.jwauth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JwBindingMapper extends BaseMapper<JwBindingEntity> {

    @Select("SELECT * FROM jw_binding WHERE user_id = #{userId} AND deleted = 0 LIMIT 1")
    JwBindingEntity findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM jw_binding WHERE sso_account = #{ssoAccount} AND deleted = 0 LIMIT 1")
    JwBindingEntity findBySsoAccount(@Param("ssoAccount") String ssoAccount);

    @Select("SELECT * FROM jw_binding WHERE identity_kind = 'STUDENT' AND auth_status = 'AUTHENTICATED' AND deleted = 0")
    java.util.List<JwBindingEntity> selectAllAuthenticatedStudents();
}
