package com.studenthub.modules.push.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "管理端模板测试发送：发往当前登录管理员（站内通知 + 已配置通道）")
public record PushTemplateTestRequest(
        @Schema(description = "模板变量取值，例如 {\"studentName\":\"李同学\"}") Map<String, String> variables,
        @Schema(description = "渠道覆盖；留空则用模板默认渠道") List<String> channels,
        @Schema(description = "标题覆盖（可选，便于灰度文案）") String titleOverride,
        @Schema(description = "正文覆盖（可选）") String contentOverride) {
}
