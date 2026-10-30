package com.studenthub.modules.community.controller.dto;

import com.studenthub.modules.community.entity.CommunityAdvertiserEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "管理端社区广告主")
public record AdminAdvertiserResponse(
        @Schema(description = "广告主 ID") Long advertiserId,
        @Schema(description = "校区 ID") Long campusId,
        @Schema(description = "广告主名称") String name,
        @Schema(description = "联系人") String contactName,
        @Schema(description = "联系电话") String contactPhone,
        @Schema(description = "状态：ENABLED/DISABLED") String status,
        @Schema(description = "备注") String remark,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt) {

    public static AdminAdvertiserResponse from(CommunityAdvertiserEntity entity) {
        return new AdminAdvertiserResponse(entity.getId(), entity.getCampusId(), entity.getName(),
                entity.getContactName(), entity.getContactPhone(), entity.getStatus(), entity.getRemark(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
