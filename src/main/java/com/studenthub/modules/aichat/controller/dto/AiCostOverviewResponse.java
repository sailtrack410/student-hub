package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理端 AI 成本看板")
public record AiCostOverviewResponse(
        @Schema(description = "统计开始日期") String rangeStart,
        @Schema(description = "统计结束日期") String rangeEnd,
        @Schema(description = "统计天数") int days,
        @Schema(description = "总览") Kpis kpis,
        @Schema(description = "按模型成本") List<ModelCost> byModel,
        @Schema(description = "按天趋势") List<TrendPoint> trend,
        @Schema(description = "全部模型单价配置（含未使用模型）") List<ModelPricing> models,
        @Schema(description = "口径说明") String pricingNote) {

    @Schema(description = "成本总览")
    public record Kpis(
            @Schema(description = "AI 请求数（assistant 消息数）") long requests,
            @Schema(description = "prompt tokens") long promptTokens,
            @Schema(description = "completion tokens") long completionTokens,
            @Schema(description = "总 tokens") long totalTokens,
            @Schema(description = "估算成本；任一使用中的模型未配置单价时为 null") BigDecimal estimatedCost,
            @Schema(description = "使用中的模型是否全部配置单价") boolean pricingConfigured,
            @Schema(description = "缺少单价的模型数") int unpricedModels) {
    }

    @Schema(description = "单模型成本")
    public record ModelCost(
            @Schema(description = "ai_model.id；消息中的模型未登记时为 null") Long modelId,
            @Schema(description = "模型 key（ai_message.model）") String modelKey,
            @Schema(description = "供应商名称") String providerName,
            @Schema(description = "模型类型") String modelType,
            @Schema(description = "请求数") long requests,
            @Schema(description = "prompt tokens") long promptTokens,
            @Schema(description = "completion tokens") long completionTokens,
            @Schema(description = "总 tokens") long totalTokens,
            @Schema(description = "输入单价/1K tokens") BigDecimal inputPricePer1k,
            @Schema(description = "输出单价/1K tokens") BigDecimal outputPricePer1k,
            @Schema(description = "单价是否完整") boolean pricingConfigured,
            @Schema(description = "估算成本；未配置单价时为 null") BigDecimal estimatedCost) {
    }

    @Schema(description = "按天趋势")
    public record TrendPoint(
            @Schema(description = "日期 yyyy-MM-dd") String date,
            @Schema(description = "请求数") long requests,
            @Schema(description = "prompt tokens") long promptTokens,
            @Schema(description = "completion tokens") long completionTokens,
            @Schema(description = "总 tokens") long totalTokens,
            @Schema(description = "当日估算成本；存在未配置单价的模型时为 null") BigDecimal estimatedCost) {
    }

    @Schema(description = "模型单价配置")
    public record ModelPricing(
            @Schema(description = "模型 ID") Long modelId,
            @Schema(description = "供应商名称") String providerName,
            @Schema(description = "供应商模型 ID") String providerModelId,
            @Schema(description = "模型类型") String modelType,
            @Schema(description = "是否启用") boolean enabled,
            @Schema(description = "输入单价/1K tokens") BigDecimal inputPricePer1k,
            @Schema(description = "输出单价/1K tokens") BigDecimal outputPricePer1k,
            @Schema(description = "单价是否完整") boolean pricingConfigured) {
    }
}
