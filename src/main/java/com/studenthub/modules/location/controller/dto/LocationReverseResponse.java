package com.studenthub.modules.location.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "逆地理编码结果")
public record LocationReverseResponse(
        @Schema(description = "格式化地址") String name,
        @Schema(description = "地址") String address,
        @Schema(description = "纬度") double latitude,
        @Schema(description = "经度") double longitude,
        @Schema(description = "区县") String district,
        @Schema(description = "城市") String city) {
}
