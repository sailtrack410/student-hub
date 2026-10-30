package com.studenthub.modules.community.controller.dto;

import com.studenthub.common.persistence.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端官方账号分页查询")
public record AdminOfficialAccountPageQuery(
        @Schema(description = "页码，从 1 开始") Integer pageNum,
        @Schema(description = "每页条数，最大 50") Integer pageSize,
        @Schema(description = "账号类型：OFFICIAL_AUTO/OFFICIAL_HUMAN/AD_SERVICE；空表示全部官方类账号") String accountType,
        @Schema(description = "关键词：展示名/用户名/运营人") String keyword,
        @Schema(description = "账号状态：ENABLED/DISABLED") String status,
        @Schema(description = "认证状态：NONE/PENDING/APPROVED/REJECTED/DISABLED") String verificationStatus) implements PageQuery {
}
