package com.studenthub.modules.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.identity.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT * FROM sys_user WHERE wechat_open_id = #{wechatOpenId} AND deleted = 0 LIMIT 1")
    UserEntity findByWechatOpenId(@Param("wechatOpenId") String wechatOpenId);
}
