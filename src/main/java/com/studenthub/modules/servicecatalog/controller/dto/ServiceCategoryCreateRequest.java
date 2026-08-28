package com.studenthub.modules.servicecatalog.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "管理端创建服务分类请求")
public record ServiceCategoryCreateRequest(
        @Schema(description = "分类编码") @NotBlank @Size(max = 64) String categoryCode,
        @Schema(description = "分类名称") @NotBlank @Size(max = 128) String categoryName,
        @Schema(description = "图标资源标识", nullable = true) @Size(max = 128) String icon,
        @Schema(description = "分类描述", nullable = true) @Size(max = 512) String description,
        @Schema(description = "排序值", nullable = true) Integer sortOrder) {
}
