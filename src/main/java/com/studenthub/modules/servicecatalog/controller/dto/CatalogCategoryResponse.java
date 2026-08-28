package com.studenthub.modules.servicecatalog.controller.dto;

import com.studenthub.modules.servicecatalog.entity.ServiceCategoryEntity;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "服务目录分类（含服务项）")
public record CatalogCategoryResponse(
        @Schema(description = "分类 ID") Long categoryId,
        @Schema(description = "分类编码") String categoryCode,
        @Schema(description = "分类名称") String categoryName,
        @Schema(description = "分类图标资源标识", nullable = true) String icon,
        @Schema(description = "分类描述", nullable = true) String description,
        @Schema(description = "分类下的服务项") List<ServiceItemSummaryResponse> items) {

    public static CatalogCategoryResponse from(ServiceCategoryEntity category, List<ServiceItemEntity> items) {
        return new CatalogCategoryResponse(category.getId(), category.getCategoryCode(), category.getCategoryName(),
                category.getIcon(), category.getDescription(),
                items.stream().map(ServiceItemSummaryResponse::from).toList());
    }
}
