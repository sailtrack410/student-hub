package com.studenthub.modules.servicerequest.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "管理端申请详情-材料/图片字段（图片已签名）")
public record AdminRequestMaterialResponse(
        @Schema(description = "表单字段 key") String fieldKey,
        @Schema(description = "表单字段名称") String label,
        @Schema(description = "字段类型") String type,
        @Schema(description = "图片短时签名地址") List<String> urls) {
}
