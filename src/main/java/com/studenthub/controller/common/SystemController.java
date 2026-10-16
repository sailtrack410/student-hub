package com.studenthub.controller.common;

import com.studenthub.common.Result;
import com.studenthub.controller.common.dto.SystemHealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/common/system")
@Tag(name = "通用-系统健康", description = "应用健康检查")
public class SystemController {

    @GetMapping("/health")
    @Operation(summary = "查询应用健康状态", description = "检查 API 服务是否可以正常响应。", operationId = "getSystemHealth")
    public Result<SystemHealthResponse> health() {
        return Result.success(new SystemHealthResponse("student-hub-api", "UP"));
    }
}
