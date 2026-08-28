package com.studenthub.modules.servicecatalog.controller.dto;

import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "服务项概要")
public record ServiceItemSummaryResponse(
        @Schema(description = "服务项 ID") Long itemId,
        @Schema(description = "服务项编码") String itemCode,
        @Schema(description = "所属分类 ID") Long categoryId,
        @Schema(description = "服务项名称") String itemName,
        @Schema(description = "一句话简介", nullable = true) String summary,
        @Schema(description = "图标资源标识", nullable = true) String icon,
        @Schema(description = "承诺办结时限（小时）", nullable = true) Integer slaHours) {

    public static ServiceItemSummaryResponse from(ServiceItemEntity entity) {
        return new ServiceItemSummaryResponse(entity.getId(), entity.getItemCode(), entity.getCategoryId(),
                entity.getItemName(), entity.getSummary(), entity.getIcon(), entity.getSlaHours());
    }
}
