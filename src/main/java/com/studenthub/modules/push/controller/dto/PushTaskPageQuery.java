package com.studenthub.modules.push.controller.dto;

import com.studenthub.common.persistence.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端推送任务分页查询")
public record PushTaskPageQuery(
        @Schema(description = "页码，从 1 开始") Integer pageNum,
        @Schema(description = "每页条数，最大 50") Integer pageSize,
        @Schema(description = "状态：DRAFT/SCHEDULED/SENDING/SENT/PARTIAL/FAILED/CANCELLED") String status,
        @Schema(description = "关键词：任务名/标题") String keyword) implements PageQuery {
}
