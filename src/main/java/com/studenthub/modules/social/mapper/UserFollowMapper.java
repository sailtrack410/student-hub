package com.studenthub.modules.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.social.entity.UserFollowEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollowEntity> {

    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
    long countByPair(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /** 互关集合：我关注的人里同时关注我的（互相关注好友） */
    @Select("SELECT f.followee_id FROM user_follow f WHERE f.follower_id = #{userId} "
            + "AND EXISTS (SELECT 1 FROM user_follow m WHERE m.follower_id = f.followee_id AND m.followee_id = #{userId})")
    List<Long> selectMutualFolloweeIds(@Param("userId") Long userId);

    @Delete("DELETE FROM user_follow WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
    int deleteByPair(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);
}
