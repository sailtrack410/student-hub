package com.studenthub.modules.homework.controller.dto;

import com.studenthub.modules.homework.entity.HomeworkEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "作业信息（管理端）")
public record AdminHomeworkResponse(
        @Schema(description = "作业 ID") Long homeworkId,
        @Schema(description = "校区 ID") Long campusId,
        @Schema(description = "课程名称") String courseName,
        @Schema(description = "作业标题") String title,
        @Schema(description = "发布教师", nullable = true) String teacherName,
        @Schema(description = "发布时间") LocalDateTime publishAt,
        @Schema(description = "截止时间") LocalDateTime dueAt,
        @Schema(description = "作业状态：PUBLISHED/CLOSED") String status,
        @Schema(description = "创建时间") LocalDateTime createdAt) {

    public static AdminHomeworkResponse from(HomeworkEntity entity) {
        return new AdminHomeworkResponse(entity.getId(), entity.getCampusId(), entity.getCourseName(),
                entity.getTitle(), entity.getTeacherName(), entity.getPublishAt(), entity.getDueAt(),
                entity.getStatus(), entity.getCreatedAt());
    }
}
