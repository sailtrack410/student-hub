package com.studenthub.modules.electricity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "宿舍电费账单（iyunmu 云端缓存值）")
public record ElectricityBillResponse(
        @Schema(description = "设备 SN") String sn,
        @Schema(description = "设备名") String name,
        @Schema(description = "收款方") String payee,
        @Schema(description = "剩余电费（元）") BigDecimal surplusValue,
        @Schema(description = "累计用电（度）") BigDecimal totalValue,
        @Schema(description = "电价（元/度）") BigDecimal unitPrice,
        @Schema(description = "通电状态：1=通电") Integer switchStatus,
        @Schema(description = "数据时间") String readTime,
        @Schema(description = "是否可在线缴费") boolean payEnable,
        @Schema(description = "通信协议") String protocols,
        @Schema(description = "设备类型") String deviceType) {
}
