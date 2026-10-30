package com.studenthub.modules.dashboard.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.dashboard.controller.dto.HolidayRequest;
import com.studenthub.modules.dashboard.controller.dto.HolidayResponse;
import com.studenthub.modules.dashboard.service.HolidayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sla/holidays")
@Tag(name = "管理端-SLA 节假日", description = "节假日与调休工作日历，供 SLA 承诺截止推算")
@RequiredArgsConstructor
public class AdminHolidayController {

    private final HolidayService holidayService;

    @GetMapping
    @Operation(summary = "节假日列表", description = "按年份查询平台级与当前校区的节假日/调休配置。",
            operationId = "adminListServiceHolidays")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('sla:view')")
    public Result<List<HolidayResponse>> list(@RequestParam(required = false) Integer year) {
        return Result.success(holidayService.list(year));
    }

    @PostMapping
    @Operation(summary = "新增节假日", operationId = "adminCreateServiceHoliday")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:config:manage')")
    public Result<HolidayResponse> create(@RequestBody @Valid HolidayRequest request) {
        return Result.success(holidayService.create(request));
    }

    @PostMapping("/{id}")
    @Operation(summary = "更新节假日", operationId = "adminUpdateServiceHoliday")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:config:manage')")
    public Result<HolidayResponse> update(@PathVariable Long id, @RequestBody @Valid HolidayRequest request) {
        return Result.success(holidayService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除节假日", description = "逻辑删除，删除后 SLA 推算不再按该日期顺延。",
            operationId = "adminDeleteServiceHoliday")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:config:manage')")
    public Result<Void> delete(@PathVariable Long id) {
        holidayService.delete(id);
        return Result.success();
    }
}
