package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "身份审核拒绝原因模板创建/更新")
public record IdentityRejectReasonRequest(
        @Schema(description = "模板编码，创建后不可修改")
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "模板编码需以大写字母开头，仅含大写字母/数字/下划线")
        String templateCode,
        @Schema(description = "原因文案（展示给审核人，可写入拒绝通知）") @NotBlank @Size(max = 255) String templateText,
        @Schema(description = "排序值，升序") Integer sortOrder,
        @Schema(description = "状态：ENABLED/DISABLED，缺省 ENABLED") @Size(max = 16) String status,
        @Schema(description = "备注") @Size(max = 255) String remark) {
}
