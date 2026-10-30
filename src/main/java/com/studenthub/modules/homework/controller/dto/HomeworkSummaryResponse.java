package com.studenthub.modules.homework.controller.dto;

import com.studenthub.modules.homework.entity.HomeworkEntity;
import com.studenthub.modules.homework.entity.HomeworkSubmissionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "作业列表项（App）")
public record HomeworkSummaryResponse(
        @Schema(description = "作业 ID") Long homeworkId,
        @Schema(description = "课程名称") String courseName,
        @Schema(description = "作业标题") String title,
        @Schema(description = "发布教师", nullable = true) String teacherName,
        @Schema(description = "发布时间") LocalDateTime publishAt,
        @Schema(description = "截止时间") LocalDateTime dueAt,
        @Schema(description = "作业状态：PUBLISHED/CLOSED") String status,
        @Schema(description = "当前用户是否已提交") boolean submitted,
        @Schema(description = "当前用户最近一次提交时间", nullable = true) LocalDateTime submittedAt) {

    public static HomeworkSummaryResponse from(HomeworkEntity entity, HomeworkSubmissionEntity submission) {
        return new HomeworkSummaryResponse(entity.getId(), entity.getCourseName(), entity.getTitle(),
                entity.getTeacherName(), entity.getPublishAt(), entity.getDueAt(), entity.getStatus(),
                submission != null, submission == null ? null : submission.getSubmittedAt());
    }
}
