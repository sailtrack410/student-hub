package com.studenthub.modules.identity.controller.dto;

import com.studenthub.modules.identity.entity.CampusEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端校区")
public record AdminCampusResponse(
        @Schema(description = "校区 ID") Long campusId,
        @Schema(description = "校区编码") String campusCode,
        @Schema(description = "校区名称") String campusName,
        @Schema(description = "状态") String status,
        @Schema(description = "组织数量") Long organizationCount,
        @Schema(description = "用户数量") Long userCount) {

    public static AdminCampusResponse from(CampusEntity entity, Long organizationCount, Long userCount) {
        return new AdminCampusResponse(entity.getId(), entity.getCampusCode(), entity.getCampusName(),
                entity.getStatus(), organizationCount == null ? 0L : organizationCount,
                userCount == null ? 0L : userCount);
    }
}
