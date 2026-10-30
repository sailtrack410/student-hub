package com.studenthub.modules.servicecatalog.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "管理端创建/更新服务项请求")
public record ServiceItemUpsertRequest(
        @Schema(description = "服务项 ID（更新时必填）", nullable = true) Long itemId,
        @Schema(description = "所属分类 ID") Long categoryId,
        @Schema(description = "服务项编码") @NotBlank @Size(max = 64) String itemCode,
        @Schema(description = "服务项名称") @NotBlank @Size(max = 128) String itemName,
        @Schema(description = "一句话简介", nullable = true) @Size(max = 512) String summary,
        @Schema(description = "办事指南", nullable = true) String description,
        @Schema(description = "图标资源标识", nullable = true) @Size(max = 128) String icon,
        @Schema(description = "动态表单字段定义 JSON") JsonNode formSchema,
        @Schema(description = "受理组织 ID", nullable = true) Long handlerOrganizationId,
        @Schema(description = "承诺办结时限（小时）", nullable = true) @Min(1) Integer slaHours,
        @Schema(description = "默认优先级：NORMAL / URGENT", nullable = true)
        @Pattern(regexp = "NORMAL|URGENT", message = "默认优先级仅支持 NORMAL 或 URGENT") String defaultPriority,
        @Schema(description = "是否需要拍照确认流程", nullable = true) Boolean photoWorkflowRequired,
        @Schema(description = "办事指南正文（S02）", nullable = true) String guideContent,
        @Schema(description = "材料清单 JSON 数组 [{name,required,note}]", nullable = true) JsonNode materialList,
        @Schema(description = "常见问题 JSON 数组 [{question,answer}]", nullable = true) JsonNode faq,
        @Schema(description = "排序值", nullable = true) Integer sortOrder) {
}
