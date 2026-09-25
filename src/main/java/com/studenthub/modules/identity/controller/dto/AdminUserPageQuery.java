package com.studenthub.modules.identity.controller.dto;

import com.studenthub.common.persistence.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "管理端用户分页查询")
public record AdminUserPageQuery(
        @Schema(description = "页码，从 1 开始") @Min(1) Integer pageNum,
        @Schema(description = "每页条数，最大 100") @Min(1) @Max(100) Integer pageSize,
        @Schema(description = "关键词，模糊匹配用户名/昵称/手机号", nullable = true) String keyword,
        @Schema(description = "账号状态过滤：ENABLED/DISABLED", nullable = true) String status,
        @Schema(description = "身份类型过滤：STUDENT/TEACHER/LOGISTICS/ADMIN", nullable = true) String identityType,
        @Schema(description = "授权状态过滤：BASIC/PENDING/AUTHORIZED/REJECTED", nullable = true) String authorizationStatus,
        @Schema(description = "按角色 ID 过滤", nullable = true) Long roleId) implements PageQuery {
}
