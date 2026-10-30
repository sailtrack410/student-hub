package com.studenthub.modules.servicerequest.controller.dto;

import com.studenthub.common.persistence.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

@Schema(description = "管理端请假审批分页查询")
public record AdminLeavePageQuery(
        @Schema(description = "页码，从 1 开始") @Min(1) Integer pageNum,
        @Schema(description = "每页条数，最大 100") @Min(1) @Max(100) Integer pageSize,
        @Schema(description = "状态：PENDING/APPROVED/REJECTED/CANCELLED（销假），空为全部") String status,
        @Schema(description = "请假类型：病假/事假/其他，空为全部") String leaveType,
        @Schema(description = "关键词：申请单号/标题/申请人姓名") String keyword,
        @Schema(description = "请假开始日期（含）") LocalDate startDate,
        @Schema(description = "请假结束日期（含）") LocalDate endDate) implements PageQuery {
}
