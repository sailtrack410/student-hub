package com.studenthub.modules.evaluation.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "管理端服务评价分页查询")
public record AdminEvaluationPageQuery(
        @Schema(description = "页码，从 1 开始") @Min(1) Integer pageNum,
        @Schema(description = "每页条数，最大 50") @Min(1) @Max(50) Integer pageSize,
        @Schema(description = "按评分过滤：1/2 差评、3 中评、4/5 好评；不传查全部", nullable = true)
        @Min(1) @Max(5) Integer rating,
        @Schema(description = "true 只看待回复（reply 为空），false 只看已回复", nullable = true) Boolean replied) {

    public int pageNumOrDefault() {
        return pageNum == null ? 1 : pageNum;
    }

    public int pageSizeOrDefault() {
        return pageSize == null ? 10 : pageSize;
    }
}
