package com.studenthub.modules.aichat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.aichat.entity.AiMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessageEntity> {

    @Select("""
            SELECT COUNT(*)
              FROM ai_message m
              JOIN ai_conversation c ON c.id = m.conversation_id
             WHERE c.user_id = #{userId}
               AND m.role = 'USER'
               AND m.created_at >= #{since}
               AND m.deleted = 0
            """)
    long countUserMessagesSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
