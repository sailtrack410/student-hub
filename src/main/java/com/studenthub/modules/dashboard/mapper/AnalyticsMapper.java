package com.studenthub.modules.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据分析聚合查询（只读）。
 *
 * <p>全部按 campus_id + 时间窗过滤，按天分组使用 CAST(created_at AS DATE)，
 * 兼容 MySQL 8 与 H2（MySQL 模式）。返回 Map 由 Service 归一化列名（H2 返回大写）。</p>
 */
@Mapper
public interface AnalyticsMapper {

    String DAY = "CAST(created_at AS DATE)";

    @Select("SELECT CAST(created_at AS DATE) AS day, COUNT(*) AS total FROM service_request "
            + "WHERE deleted = 0 AND campus_id = #{campusId} AND created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY CAST(created_at AS DATE) ORDER BY day")
    List<Map<String, Object>> requestsByDay(@Param("campusId") Long campusId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    @Select("SELECT CAST(created_at AS DATE) AS day, COUNT(*) AS total FROM work_order "
            + "WHERE flow_archived = FALSE AND request_id IN (SELECT id FROM service_request WHERE flow_type = 'REPAIR' AND deleted = 0) AND deleted = 0 AND campus_id = #{campusId} AND created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY CAST(created_at AS DATE) ORDER BY day")
    List<Map<String, Object>> workOrdersByDay(@Param("campusId") Long campusId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    @Select("SELECT CAST(created_at AS DATE) AS day, COUNT(*) AS total FROM community_post "
            + "WHERE deleted = 0 AND campus_id = #{campusId} AND created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY CAST(created_at AS DATE) ORDER BY day")
    List<Map<String, Object>> postsByDay(@Param("campusId") Long campusId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    @Select("SELECT CAST(c.created_at AS DATE) AS day, COUNT(*) AS total FROM community_comment c "
            + "JOIN community_post p ON c.post_id = p.id "
            + "WHERE c.deleted = 0 AND p.deleted = 0 AND p.campus_id = #{campusId} "
            + "AND c.created_at >= #{start} AND c.created_at < #{end} "
            + "GROUP BY CAST(c.created_at AS DATE) ORDER BY day")
    List<Map<String, Object>> commentsByDay(@Param("campusId") Long campusId,
                                           @Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    @Select("SELECT CAST(created_at AS DATE) AS day, COUNT(*) AS total FROM sys_user "
            + "WHERE deleted = 0 AND campus_id = #{campusId} AND created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY CAST(created_at AS DATE) ORDER BY day")
    List<Map<String, Object>> newUsersByDay(@Param("campusId") Long campusId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    @Select("SELECT CAST(c.created_at AS DATE) AS day, COUNT(*) AS total FROM ai_conversation c "
            + "JOIN sys_user u ON c.user_id = u.id "
            + "WHERE c.deleted = 0 AND u.deleted = 0 AND u.campus_id = #{campusId} "
            + "AND c.created_at >= #{start} AND c.created_at < #{end} "
            + "GROUP BY CAST(c.created_at AS DATE) ORDER BY day")
    List<Map<String, Object>> aiSessionsByDay(@Param("campusId") Long campusId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM community_comment c JOIN community_post p ON c.post_id = p.id "
            + "WHERE c.deleted = 0 AND p.deleted = 0 AND p.campus_id = #{campusId} "
            + "AND c.created_at >= #{start} AND c.created_at < #{end}")
    long countComments(@Param("campusId") Long campusId,
                       @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM ai_message m JOIN ai_conversation c ON m.conversation_id = c.id "
            + "JOIN sys_user u ON c.user_id = u.id "
            + "WHERE m.deleted = 0 AND c.deleted = 0 AND u.deleted = 0 AND u.campus_id = #{campusId} "
            + "AND m.created_at >= #{start} AND m.created_at < #{end}")
    long countAiMessages(@Param("campusId") Long campusId,
                         @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM ("
            + "SELECT requester_id AS uid FROM service_request WHERE deleted = 0 AND campus_id = #{campusId} "
            + "AND created_at >= #{start} AND created_at < #{end} "
            + "UNION SELECT p.author_id FROM community_post p WHERE p.deleted = 0 AND p.campus_id = #{campusId} "
            + "AND p.created_at >= #{start} AND p.created_at < #{end} "
            + "UNION SELECT c.author_id FROM community_comment c JOIN community_post p ON c.post_id = p.id "
            + "WHERE c.deleted = 0 AND p.deleted = 0 AND p.campus_id = #{campusId} "
            + "AND c.created_at >= #{start} AND c.created_at < #{end} "
            + "UNION SELECT c.user_id FROM ai_conversation c JOIN sys_user u ON c.user_id = u.id "
            + "WHERE c.deleted = 0 AND u.deleted = 0 AND u.campus_id = #{campusId} "
            + "AND c.created_at >= #{start} AND c.created_at < #{end}"
            + ") active_users")
    long countActiveUsers(@Param("campusId") Long campusId,
                          @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT status AS name, COUNT(*) AS total FROM service_request "
            + "WHERE deleted = 0 AND campus_id = #{campusId} AND created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY status ORDER BY total DESC")
    List<Map<String, Object>> requestStatusSlices(@Param("campusId") Long campusId,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);

    @Select("SELECT status AS name, COUNT(*) AS total FROM work_order "
            + "WHERE flow_archived = FALSE AND request_id IN (SELECT id FROM service_request WHERE flow_type = 'REPAIR' AND deleted = 0) AND deleted = 0 AND campus_id = #{campusId} AND created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY status ORDER BY total DESC")
    List<Map<String, Object>> workOrderStatusSlices(@Param("campusId") Long campusId,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);

    @Select("SELECT item_name AS name, COUNT(*) AS total FROM service_request "
            + "WHERE deleted = 0 AND campus_id = #{campusId} AND created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY item_name ORDER BY total DESC LIMIT 8")
    List<Map<String, Object>> topServiceSlices(@Param("campusId") Long campusId,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    @Select("SELECT s.section_name AS name, COUNT(*) AS total FROM community_post p "
            + "JOIN community_section s ON p.section_id = s.id "
            + "WHERE p.deleted = 0 AND s.deleted = 0 AND p.campus_id = #{campusId} "
            + "AND p.created_at >= #{start} AND p.created_at < #{end} "
            + "GROUP BY s.section_name ORDER BY total DESC LIMIT 8")
    List<Map<String, Object>> topSectionSlices(@Param("campusId") Long campusId,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    @Select("SELECT identity_type AS name, COUNT(*) AS total FROM sys_user "
            + "WHERE deleted = 0 AND campus_id = #{campusId} AND created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY identity_type ORDER BY total DESC")
    List<Map<String, Object>> userIdentitySlices(@Param("campusId") Long campusId,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);

    @Select("SELECT status AS name, COUNT(*) AS total FROM community_report "
            + "WHERE deleted = 0 AND campus_id = #{campusId} AND created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY status ORDER BY total DESC")
    List<Map<String, Object>> reportStatusSlices(@Param("campusId") Long campusId,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);
}
