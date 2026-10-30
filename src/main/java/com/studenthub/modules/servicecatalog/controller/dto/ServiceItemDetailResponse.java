package com.studenthub.modules.servicecatalog.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "服务项详情")
public record ServiceItemDetailResponse(
        @Schema(description = "服务项 ID") Long itemId,
        @Schema(description = "服务项编码") String itemCode,
        @Schema(description = "服务项名称") String itemName,
        @Schema(description = "一句话简介", nullable = true) String summary,
        @Schema(description = "办事指南", nullable = true) String description,
        @Schema(description = "图标资源标识", nullable = true) String icon,
        @Schema(description = "动态申请表单字段定义") JsonNode formSchema,
        @Schema(description = "承诺办结时限（小时）", nullable = true) Integer slaHours,
        @Schema(description = "默认优先级", nullable = true) String defaultPriority,
        @Schema(description = "是否需要拍照确认流程") boolean photoWorkflowRequired,
        @Schema(description = "办事指南正文（S02）", nullable = true) String guideContent,
        @Schema(description = "材料清单 JSON 数组（S02）", nullable = true) JsonNode materialList,
        @Schema(description = "常见问题 JSON 数组（S02）", nullable = true) JsonNode faq) {
}
