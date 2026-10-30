package com.studenthub.modules.location.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "附近地点")
public record LocationPlaceResponse(
        @Schema(description = "POI ID") String id,
        @Schema(description = "名称") String name,
        @Schema(description = "地址") String address,
        @Schema(description = "纬度") double latitude,
        @Schema(description = "经度") double longitude,
        @Schema(description = "距离（米）") double distance,
        @Schema(description = "类型") String type,
        @Schema(description = "所在区县") String district) {
}
