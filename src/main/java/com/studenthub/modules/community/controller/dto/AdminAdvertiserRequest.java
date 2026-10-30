package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "管理端社区广告主创建/编辑")
public record AdminAdvertiserRequest(
        @Schema(description = "广告主名称") @NotBlank @Size(max = 100) String name,
        @Schema(description = "联系人") @Size(max = 50) String contactName,
        @Schema(description = "联系电话") @Size(max = 30) String contactPhone,
        @Schema(description = "状态：ENABLED/DISABLED，缺省 ENABLED") @Size(max = 16) String status,
        @Schema(description = "备注") @Size(max = 500) String remark) {
}
