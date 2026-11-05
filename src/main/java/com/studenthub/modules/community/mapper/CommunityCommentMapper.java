package com.studenthub.modules.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.community.entity.CommunityCommentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommunityCommentMapper extends BaseMapper<CommunityCommentEntity> {

    /** D1 原子计数：并发点赞同评论时避免互相覆盖丢计数（下限钳 0） */
    @Update("UPDATE community_comment SET like_count = GREATEST(like_count + #{delta}, 0), "
            + "weight = GREATEST(weight + #{delta}, 0), updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id}")
    int adjustLikeCount(@Param("id") Long id, @Param("delta") int delta);
}
