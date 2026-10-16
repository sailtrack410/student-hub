package com.studenthub.modules.servicecatalog.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
@Schema(description = "管理端服务项概要")
public record ServiceItemAdminResponse(
        @Schema(description = "服务项 ID") Long itemId,
        @Schema(description = "所属分类 ID") Long categoryId,
        @Schema(description = "所属分类名称") String categoryName,
        @Schema(description = "服务项编码") String itemCode,
        @Schema(description = "服务项名称") String itemName,
        @Schema(description = "一句话简介", nullable = true) String summary,
        @Schema(description = "办事指南", nullable = true) String description,
        @Schema(description = "图标资源标识", nullable = true) String icon,
        @Schema(description = "状态：ENABLED 已上线 / DISABLED 已停用") String status,
        @Schema(description = "承诺办结时限（小时）", nullable = true) Integer slaHours,
        @Schema(description = "排序值") Integer sortOrder,
        @Schema(description = "受理组织 ID", nullable = true) Long handlerOrganizationId,
        @Schema(description = "动态申请表单字段定义") JsonNode formSchema,
        @Schema(description = "更新时间") LocalDateTime updatedAt) {

    public static ServiceItemAdminResponse from(ServiceItemEntity entity, String categoryName, JsonNode formSchema) {
        return new ServiceItemAdminResponse(entity.getId(), entity.getCategoryId(), categoryName,
                entity.getItemCode(), entity.getItemName(), entity.getSummary(), entity.getDescription(),
                entity.getIcon(),
                entity.getStatus(), entity.getSlaHours(), entity.getSortOrder(),
                entity.getHandlerOrganizationId(), formSchema, entity.getUpdatedAt());
    }
}
