package com.studenthub.modules.homework.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(description = "管理端发布作业请求")
public record CreateHomeworkRequest(
        @Schema(description = "课程名称", example = "软件工程导论") @NotBlank(message = "课程名称不能为空")
        @Size(max = 128, message = "课程名称不能超过 128 个字符") String courseName,
        @Schema(description = "作业标题", example = "第三章课后作业") @NotBlank(message = "作业标题不能为空")
        @Size(max = 200, message = "作业标题不能超过 200 个字符") String title,
        @Schema(description = "作业内容/要求") @NotBlank(message = "作业内容不能为空")
        @Size(max = 5000, message = "作业内容不能超过 5000 个字符") String content,
        @Schema(description = "发布教师姓名；不填默认使用当前操作人姓名", nullable = true)
        @Size(max = 64, message = "教师姓名不能超过 64 个字符") String teacherName,
        @Schema(description = "发布时间；不填默认当前时间", nullable = true) LocalDateTime publishAt,
        @Schema(description = "截止时间，必须晚于发布时间", example = "2026-10-30T18:00:00")
        @NotNull(message = "截止时间不能为空") LocalDateTime dueAt
) {
}
