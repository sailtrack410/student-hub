package com.studenthub.modules.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.community.entity.CommunityPostEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommunityPostMapper extends BaseMapper<CommunityPostEntity> {

    /** 申诉场景需要读取被逻辑删除的帖子，普通查询仍走 MyBatis-Plus 逻辑删除 */
    @Select("SELECT * FROM community_post WHERE id = #{id} LIMIT 1")
    CommunityPostEntity selectByIdIncludingDeleted(@Param("id") Long id);

    /** 申诉通过后恢复帖子（可恢复被屏蔽或作者删除的帖子） */
    @Update("UPDATE community_post SET status = 'ENABLED', hidden_reason = NULL, hidden_at = NULL, "
            + "deleted = 0, updated_at = CURRENT_TIMESTAMP, updated_by = #{operatorId}, version = version + 1 "
            + "WHERE id = #{id}")
    int restoreFromAppeal(@Param("id") Long id, @Param("operatorId") Long operatorId);

    @Select("SELECT * FROM community_post WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    CommunityPostEntity selectByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE community_post SET comment_count = GREATEST(COALESCE(comment_count, 0) + #{delta}, 0), "
            + "version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int adjustCommentCount(@Param("id") Long id, @Param("delta") int delta);

    /** D1 原子计数：并发点赞同帖时避免「读-改-整行写」互相覆盖丢计数（下限钳 0） */
    @Update("UPDATE community_post SET like_count = GREATEST(COALESCE(like_count, 0) + #{delta}, 0), "
            + "version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int adjustLikeCount(@Param("id") Long id, @Param("delta") int delta);
}
