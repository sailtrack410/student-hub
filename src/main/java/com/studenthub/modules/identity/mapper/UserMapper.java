package com.studenthub.modules.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.identity.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT * FROM sys_user WHERE wechat_open_id = #{wechatOpenId} AND deleted = 0 LIMIT 1")
    UserEntity findByWechatOpenId(@Param("wechatOpenId") String wechatOpenId);

    @Select("SELECT * FROM sys_user WHERE qq_open_id = #{qqOpenId} AND deleted = 0 LIMIT 1")
    UserEntity findByQqOpenId(@Param("qqOpenId") String qqOpenId);

    @Update("UPDATE sys_user SET worn_badge_code = NULL, updated_at = CURRENT_TIMESTAMP "
            + "WHERE worn_badge_code = #{badgeCode} AND deleted = 0")
    int clearWornBadgeByCode(@Param("badgeCode") String badgeCode);

    @Select("""
            SELECT DISTINCT u.id AS userId, u.username, u.display_name AS displayName, u.phone
              FROM sys_user u
              JOIN sys_user_role ur ON ur.user_id = u.id
              JOIN sys_role r ON r.id = ur.role_id
             WHERE r.role_code = 'WORKER'
               AND u.campus_id = #{campusId}
               AND r.status = 'ENABLED'
               AND r.deleted = 0
               AND u.status = 'ENABLED'
               AND u.deleted = 0
             ORDER BY u.id
            """)
    List<WorkerCandidateRow> findEnabledWorkers(@Param("campusId") Long campusId);
}
