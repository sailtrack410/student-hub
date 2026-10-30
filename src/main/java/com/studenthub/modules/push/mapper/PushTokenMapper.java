package com.studenthub.modules.push.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.push.entity.PushTokenEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PushTokenMapper extends BaseMapper<PushTokenEntity> {
    @Select("SELECT * FROM sys_push_token WHERE token = #{token} LIMIT 1")
    PushTokenEntity findByToken(@Param("token") String token);

    @Select("SELECT * FROM sys_push_token WHERE user_id = #{userId}")
    List<PushTokenEntity> findByUser(@Param("userId") Long userId);
}
