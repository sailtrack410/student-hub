package com.studenthub.modules.location.controller;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.location.controller.dto.LocationPlaceResponse;
import com.studenthub.modules.location.controller.dto.LocationReverseResponse;
import com.studenthub.modules.location.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app/locations")
@Tag(name = "小程序-定位", description = "高德附近地点与逆地理编码代理")
@RequiredArgsConstructor
public class AppLocationController {
    private final LocationService locationService;

    @GetMapping("/nearby")
    @Operation(summary = "附近地点", description = "按经纬度查询附近 POI；keyword 可选，radius 默认 1000 米。",
            operationId = "getNearbyLocations")
    public Result<List<LocationPlaceResponse>> nearby(@RequestParam double latitude,
                                                      @RequestParam double longitude,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1000") int radius) {
        validate(latitude, longitude);
        return Result.success(locationService.nearby(latitude, longitude, keyword,
                Math.max(100, Math.min(radius, 5000))));
    }

    @GetMapping("/reverse")
    @Operation(summary = "逆地理编码", description = "经纬度转格式化地址。", operationId = "reverseGeocode")
    public Result<LocationReverseResponse> reverse(@RequestParam double latitude,
                                                    @RequestParam double longitude) {
        validate(latitude, longitude);
        return Result.success(locationService.reverse(latitude, longitude));
    }

    private void validate(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "经纬度不合法");
        }
    }
}
