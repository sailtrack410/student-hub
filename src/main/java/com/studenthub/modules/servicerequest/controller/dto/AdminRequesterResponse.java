package com.studenthub.modules.servicerequest.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端申请详情-申请人信息")
public record AdminRequesterResponse(
        @Schema(description = "申请人用户 ID") Long userId,
        @Schema(description = "申请人姓名") String displayName,
        @Schema(description = "手机号（脱敏）", nullable = true) String phoneMasked,
        @Schema(description = "所属组织 ID", nullable = true) Long organizationId,
        @Schema(description = "所属组织名称", nullable = true) String organizationName,
        @Schema(description = "身份类型", nullable = true) String identityType) {
}
