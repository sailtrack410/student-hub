package com.studenthub.modules.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.community.entity.CommunityDirectMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface CommunityDirectMessageMapper extends BaseMapper<CommunityDirectMessageEntity> {
    @Select("SELECT * FROM community_direct_message WHERE campus_id = #{campusId} AND deleted = 0 "
            + "AND ((sender_id = #{userId} AND receiver_id = #{withUserId}) "
            + "OR (sender_id = #{withUserId} AND receiver_id = #{userId})) "
            + "ORDER BY created_at ASC LIMIT 200")
    List<CommunityDirectMessageEntity> conversation(@Param("campusId") Long campusId,
                                                     @Param("userId") Long userId,
                                                     @Param("withUserId") Long withUserId);

    @Select("SELECT * FROM community_direct_message WHERE campus_id = #{campusId} AND deleted = 0 "
            + "AND (sender_id = #{userId} OR receiver_id = #{userId}) ORDER BY created_at DESC LIMIT 200")
    List<CommunityDirectMessageEntity> contacts(@Param("campusId") Long campusId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM community_direct_message WHERE campus_id = #{campusId} "
            + "AND receiver_id = #{userId} AND deleted = 0 AND read_at IS NULL")
    long unreadCount(@Param("campusId") Long campusId, @Param("userId") Long userId);

    @Select("SELECT sender_id AS senderId, COUNT(*) AS cnt FROM community_direct_message "
            + "WHERE campus_id = #{campusId} AND receiver_id = #{userId} AND deleted = 0 AND read_at IS NULL "
            + "GROUP BY sender_id")
    List<Map<String, Object>> unreadByPeer(@Param("campusId") Long campusId, @Param("userId") Long userId);

    @Update("UPDATE community_direct_message SET read_at = CURRENT_TIMESTAMP "
            + "WHERE campus_id = #{campusId} AND receiver_id = #{userId} AND sender_id = #{withUserId} "
            + "AND deleted = 0 AND read_at IS NULL")
    int markConversationRead(@Param("campusId") Long campusId, @Param("userId") Long userId,
                             @Param("withUserId") Long withUserId);
}
