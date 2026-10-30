package com.studenthub.modules.community.controller.dto;

import com.studenthub.modules.identity.entity.UserEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "管理端官方账号")
public record AdminOfficialAccountResponse(
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "登录用户名") String username,
        @Schema(description = "账号类型") String accountType,
        @Schema(description = "社区展示名") String displayName,
        @Schema(description = "运营人") String operatorName,
        @Schema(description = "官方头衔/展示标识") String officialTitle,
        @Schema(description = "账号简介") String description,
        @Schema(description = "认证状态") String verificationStatus,
        @Schema(description = "账号状态") String status,
        @Schema(description = "是否在社区展示官方标识") boolean official,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt) {

    public static AdminOfficialAccountResponse from(UserEntity user) {
        return new AdminOfficialAccountResponse(user.getId(), user.getUsername(),
                user.getAccountType() == null ? "NORMAL" : user.getAccountType(),
                user.getDisplayName(), user.getAccountOperator(), user.getOfficialTitle(),
                user.getAccountDescription(), user.getVerificationStatus(), user.getStatus(),
                Boolean.TRUE.equals(user.getOfficial()), user.getCreatedAt(), user.getUpdatedAt());
    }
}
