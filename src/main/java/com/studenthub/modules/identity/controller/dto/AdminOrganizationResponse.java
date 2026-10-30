package com.studenthub.modules.identity.controller.dto;

import com.studenthub.modules.identity.entity.OrganizationEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端组织节点")
public record AdminOrganizationResponse(
        @Schema(description = "组织 ID") Long organizationId,
        @Schema(description = "父组织 ID") Long parentId,
        @Schema(description = "校区 ID；校级/跨校区节点为空") Long campusId,
        @Schema(description = "校区范围：CAMPUS/SCHOOL/MULTI") String campusScope,
        @Schema(description = "组织编码") String organizationCode,
        @Schema(description = "组织名称") String organizationName,
        @Schema(description = "组织类型") String organizationType,
        @Schema(description = "排序") Integer sortOrder,
        @Schema(description = "状态") String status,
        @Schema(description = "组织负责人用户 ID", nullable = true) Long leaderId,
        @Schema(description = "组织负责人昵称", nullable = true) String leaderName,
        @Schema(description = "层级深度，根组织为 1") int level,
        @Schema(description = "直接子组织数") long childCount,
        @Schema(description = "直属用户数（默认组织或角色绑定到该组织）") long userCount) {

    /**
     * 兼容旧调用点：不带负责人/统计信息的节点（level 按 1 处理）。
     */
    public static AdminOrganizationResponse from(OrganizationEntity o) {
        return from(o, null, 1, 0, 0);
    }

    public static AdminOrganizationResponse from(OrganizationEntity o, String leaderName, int level,
                                                 long childCount, long userCount) {
        return new AdminOrganizationResponse(o.getId(), o.getParentId(), o.getCampusId(), o.getCampusScope(),
                o.getOrganizationCode(), o.getOrganizationName(), o.getOrganizationType(),
                o.getSortOrder(), o.getStatus(), o.getLeaderId(), leaderName, level, childCount, userCount);
    }
}
