package com.studenthub.modules.homework.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "学生提交作业请求")
public record SubmitHomeworkRequest(
        @Schema(description = "作业提交内容", example = "1. 需求分析…… 2. 用例图……")
        @NotBlank(message = "提交内容不能为空")
        @Size(max = 5000, message = "提交内容不能超过 5000 个字符") String content
) {
}
