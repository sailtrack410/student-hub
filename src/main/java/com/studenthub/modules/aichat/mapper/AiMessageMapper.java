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

    /** AI 成本看板：按模型聚合（仅 assistant 消息；按当前校区用户过滤）。 */
    @Select("""
            SELECT COALESCE(m.model, 'UNKNOWN') AS model_key,
                   COUNT(*) AS requests,
                   COALESCE(SUM(m.prompt_tokens), 0) AS prompt_tokens,
                   COALESCE(SUM(m.completion_tokens), 0) AS completion_tokens
              FROM ai_message m
              JOIN ai_conversation c ON c.id = m.conversation_id
              JOIN sys_user u ON u.id = c.user_id
             WHERE m.deleted = 0 AND c.deleted = 0 AND u.deleted = 0
               AND u.campus_id = #{campusId}
               AND m.role = 'ASSISTANT'
               AND m.created_at >= #{start} AND m.created_at < #{end}
             GROUP BY COALESCE(m.model, 'UNKNOWN')
             ORDER BY requests DESC
            """)
    java.util.List<java.util.Map<String, Object>> aggregateCostByModel(@Param("campusId") Long campusId,
                                                                        @Param("start") LocalDateTime start,
                                                                        @Param("end") LocalDateTime end);

    /** AI 成本看板：按天 + 模型聚合，用于趋势与每日成本（任一模型未配置单价则当日成本为 null）。 */
    @Select("""
            SELECT CAST(m.created_at AS DATE) AS stat_date,
                   COALESCE(m.model, 'UNKNOWN') AS model_key,
                   COUNT(*) AS requests,
                   COALESCE(SUM(m.prompt_tokens), 0) AS prompt_tokens,
                   COALESCE(SUM(m.completion_tokens), 0) AS completion_tokens
              FROM ai_message m
              JOIN ai_conversation c ON c.id = m.conversation_id
              JOIN sys_user u ON u.id = c.user_id
             WHERE m.deleted = 0 AND c.deleted = 0 AND u.deleted = 0
               AND u.campus_id = #{campusId}
               AND m.role = 'ASSISTANT'
               AND m.created_at >= #{start} AND m.created_at < #{end}
             GROUP BY CAST(m.created_at AS DATE), COALESCE(m.model, 'UNKNOWN')
             ORDER BY stat_date
            """)
    java.util.List<java.util.Map<String, Object>> aggregateCostByDayAndModel(@Param("campusId") Long campusId,
                                                                             @Param("start") LocalDateTime start,
                                                                             @Param("end") LocalDateTime end);
}
