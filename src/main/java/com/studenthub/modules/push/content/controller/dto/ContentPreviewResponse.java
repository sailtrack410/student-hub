package com.studenthub.modules.push.content.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "内容预览：返回渲染字段 + 校验告警 + 冲突排期，供管理端提交前确认")
public record ContentPreviewResponse(
        @Schema(description = "是否通过基础校验（false 时前端应阻止上线）") boolean valid,
        @Schema(description = "告警列表（空表示无风险）") List<String> warnings,
        @Schema(description = "与同位置/同运营位重叠的在线内容编码或标题") List<String> conflicts,
        @Schema(description = "预览渲染数据（标题/副标题/图片/跳转/时间窗/运行态）") Map<String, Object> payload) {
}
