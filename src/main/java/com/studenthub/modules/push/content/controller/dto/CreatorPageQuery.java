package com.studenthub.modules.push.content.controller.dto;

import com.studenthub.common.persistence.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端创作者的筛选与分页")
public record CreatorPageQuery(
        @Schema(description = "页码，从 1 开始") Integer pageNum,
        @Schema(description = "每页条数，最大 50") Integer pageSize,
        @Schema(description = "关键词：昵称/用户 ID") String keyword,
        @Schema(description = "认证状态：NONE/PENDING/APPROVED/REJECTED/DISABLED") String certificationStatus,
        @Schema(description = "账号状态：NORMAL/MUTED/BANNED") String status,
        @Schema(description = "等级 1-5") Integer level) implements PageQuery {
}
