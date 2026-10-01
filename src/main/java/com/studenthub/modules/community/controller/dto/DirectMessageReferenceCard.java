package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 私信关联场景卡片（API需求清单 二-6）：服务端按 referenceType+referenceId 查源内容生成快照落库，
 * 源帖删除后卡片仍可展示；仅支持 POST，其余类型返回 null。
 */
@Schema(description = "私信关联卡片")
public record DirectMessageReferenceCard(
        @Schema(description = "关联类型：POST") String type,
        @Schema(description = "关联业务 ID") Long id,
        @Schema(description = "帖子标题") String title,
        @Schema(description = "封面缩略图（首图签名路径，无图 null）", nullable = true) String thumbnail) {
}
