package com.studenthub.modules.homework.controller.dto;

import com.studenthub.common.persistence.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "App 作业中心分页查询")
public record HomeworkPageQuery(
        @Schema(description = "页码，从 1 开始", example = "1") @Min(1) Integer pageNum,
        @Schema(description = "每页条数，最大 50", example = "10") @Min(1) @Max(50) Integer pageSize
) implements PageQuery {
}
