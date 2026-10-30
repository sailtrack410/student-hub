package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "管理端官方账号创建/编辑")
public record AdminOfficialAccountRequest(
        @Schema(description = "账号类型：OFFICIAL_AUTO（官网抓取）/ OFFICIAL_HUMAN（人工运营）/ AD_SERVICE（广告服务）")
        @NotBlank @Size(max = 32) String accountType,
        @Schema(description = "社区展示名") @NotBlank @Size(max = 128) String displayName,
        @Schema(description = "运营人（人工运营号必填）") @Size(max = 128) String operatorName,
        @Schema(description = "账号简介") @Size(max = 500) String description,
        @Schema(description = "官方头衔/展示标识，如「教务处」") @Size(max = 64) String officialTitle,
        @Schema(description = "认证状态：NONE/PENDING/APPROVED/REJECTED/DISABLED，缺省 PENDING")
        @Size(max = 16) String verificationStatus,
        @Schema(description = "账号状态：ENABLED/DISABLED，缺省 ENABLED") @Size(max = 16) String status,
        @Schema(description = "登录用户名，缺省自动生成；创建后不可修改") @Size(max = 64) String username) {
}
