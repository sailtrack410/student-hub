package com.studenthub.modules.identity.controller.dto;

import com.studenthub.modules.identity.entity.IdentityRejectReasonEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "身份审核拒绝原因模板")
public record IdentityRejectReasonResponse(
        Long id,
        Long campusId,
        String templateCode,
        String templateText,
        Integer sortOrder,
        String status,
        @Schema(description = "是否平台内置（内置不可修改/删除）") Boolean builtin,
        String remark,
        LocalDateTime updatedAt) {

    public static IdentityRejectReasonResponse from(IdentityRejectReasonEntity entity) {
        return new IdentityRejectReasonResponse(
                entity.getId(), entity.getCampusId(), entity.getTemplateCode(), entity.getTemplateText(),
                entity.getSortOrder(), entity.getStatus(), entity.getBuiltin(), entity.getRemark(),
                entity.getUpdatedAt());
    }
}
