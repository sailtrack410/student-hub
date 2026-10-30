package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

@Schema(description = "模型单价设置请求：每 1K tokens；两项均传 null 表示清除单价（成本将返回 null）")
public record AiModelPricingRequest(
        @Schema(description = "输入（prompt）单价/1K tokens，null 表示未配置")
        @DecimalMin(value = "0", message = "输入单价不能为负数")
        @Digits(integer = 8, fraction = 6, message = "输入单价最多 8 位整数、6 位小数")
        BigDecimal inputPricePer1k,
        @Schema(description = "输出（completion）单价/1K tokens，null 表示未配置")
        @DecimalMin(value = "0", message = "输出单价不能为负数")
        @Digits(integer = 8, fraction = 6, message = "输出单价最多 8 位整数、6 位小数")
        BigDecimal outputPricePer1k) {
}
