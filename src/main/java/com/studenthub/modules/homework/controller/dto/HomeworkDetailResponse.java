package com.studenthub.modules.homework.controller.dto;

import com.studenthub.modules.homework.entity.HomeworkEntity;
import com.studenthub.modules.homework.entity.HomeworkSubmissionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "作业详情（App）")
public record HomeworkDetailResponse(
        @Schema(description = "作业 ID") Long homeworkId,
        @Schema(description = "校区 ID") Long campusId,
        @Schema(description = "课程名称") String courseName,
        @Schema(description = "作业标题") String title,
        @Schema(description = "作业内容/要求") String content,
        @Schema(description = "发布教师", nullable = true) String teacherName,
        @Schema(description = "发布时间") LocalDateTime publishAt,
        @Schema(description = "截止时间") LocalDateTime dueAt,
        @Schema(description = "作业状态：PUBLISHED/CLOSED") String status,
        @Schema(description = "当前是否允许提交（PUBLISHED 且未过截止时间）") boolean submittable,
        @Schema(description = "当前用户是否已提交") boolean submitted,
        @Schema(description = "当前用户提交记录 ID", nullable = true) Long submissionId,
        @Schema(description = "当前用户提交内容", nullable = true) String submissionContent,
        @Schema(description = "当前用户最近一次提交时间", nullable = true) LocalDateTime submittedAt) {

    public static HomeworkDetailResponse from(HomeworkEntity entity, HomeworkSubmissionEntity submission) {
        boolean submittable = HomeworkEntity.STATUS_PUBLISHED.equals(entity.getStatus())
                && entity.getDueAt() != null
                && !LocalDateTime.now().isAfter(entity.getDueAt());
        return new HomeworkDetailResponse(entity.getId(), entity.getCampusId(), entity.getCourseName(),
                entity.getTitle(), entity.getContent(), entity.getTeacherName(), entity.getPublishAt(),
                entity.getDueAt(), entity.getStatus(), submittable, submission != null,
                submission == null ? null : submission.getId(),
                submission == null ? null : submission.getContent(),
                submission == null ? null : submission.getSubmittedAt());
    }
}
