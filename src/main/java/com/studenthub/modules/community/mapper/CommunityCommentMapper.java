package com.studenthub.modules.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.community.entity.CommunityCommentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommunityCommentMapper extends BaseMapper<CommunityCommentEntity> {

    @Select("SELECT * FROM community_comment WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    CommunityCommentEntity selectByIdForUpdate(@Param("id") Long id);

    /** D1 原子计数：并发点赞同评论时避免互相覆盖丢计数（下限钳 0） */
    @Update("UPDATE community_comment SET like_count = GREATEST(COALESCE(like_count, 0) + #{delta}, 0), "
            + "weight = GREATEST(COALESCE(weight, 0) + #{delta}, 0), version = version + 1, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND deleted = 0")
    int adjustLikeCount(@Param("id") Long id, @Param("delta") int delta);
}
