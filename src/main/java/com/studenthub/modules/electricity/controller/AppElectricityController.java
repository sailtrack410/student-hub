package com.studenthub.modules.electricity.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.electricity.controller.dto.ElectricityBillResponse;
import com.studenthub.modules.electricity.service.ElectricityService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/electricity")
@Tag(name = "小程序-宿舍电费", description = "按电表 SN 查询余额/用量（扫码获得 SN）")
@RequiredArgsConstructor
public class AppElectricityController {
    private final ElectricityService electricityService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "查询宿舍电费", description = "sn 为二维码中的 YM 开头设备 SN，云端返回缓存值。",
            operationId = "getElectricityBill")
    public Result<ElectricityBillResponse> query(@RequestParam String sn) {
        return Result.success(electricityService.query(
                currentUserProvider.requireCurrentUser(), sn));
    }
}
