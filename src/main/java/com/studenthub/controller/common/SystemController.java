package com.studenthub.controller.common;

import com.studenthub.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/common/system")
@Tag(name = "公共系统接口")
public class SystemController {

    @GetMapping("/health")
    @Operation(summary = "查询应用健康状态", operationId = "getSystemHealth")
    public Result<Map<String, String>> health() {
        return Result.success(Map.of("status", "UP", "service", "student-hub-api"));
    }
}
