package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "组织新建/更新请求")
public record AdminOrganizationRequest(
        @Schema(description = "父组织 ID，可为空表示根节点") Long parentId,
        @Schema(description = "校区 ID；campusScope=CAMPUS 时必填，校级/跨校区节点忽略") Long campusId,
        @Schema(description = "校区范围：CAMPUS 具体校区 / SCHOOL 校级 / MULTI 跨校区；为空按 CAMPUS") String campusScope,
        @Schema(description = "组织编码，全局唯一") @NotBlank @Size(max = 64) String organizationCode,
        @Schema(description = "组织名称") @NotBlank @Size(max = 128) String organizationName,
        @Schema(description = "组织类型：COLLEGE/DEPARTMENT/OFFICE/CLASS 等；根节点固定 ROOT") @NotBlank @Size(max = 32) String organizationType,
        @Schema(description = "排序") Integer sortOrder,
        @Schema(description = "状态 ENABLED/DISABLED") @NotBlank String status,
        @Schema(description = "组织负责人用户 ID，可为空表示未指定", nullable = true) Long leaderId) {
}
