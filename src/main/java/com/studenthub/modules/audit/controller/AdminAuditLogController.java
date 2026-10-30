package com.studenthub.modules.audit.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.audit.controller.dto.AdminAuditLogResponse;
import com.studenthub.modules.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/audit-logs")
@Tag(name = "管理端-审计日志", description = "关键操作审计：成功/失败写操作、变更前后 JSON、全量导出")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "审计日志分页", description = "支持按操作人/动作/资源/结果（SUCCESS/FAILED）/时间过滤。",
            operationId = "adminPageAuditLogs")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:audit:view')")
    public Result<PageResult<AdminAuditLogResponse>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) LocalDateTime startAt,
            @RequestParam(required = false) LocalDateTime endAt) {
        return Result.success(auditLogService.page(pageNum, pageSize, operatorId, action,
                resourceType, resultStatus, startAt, endAt));
    }

    @GetMapping("/export")
    @Operation(summary = "导出审计日志 CSV（全量，不走分页）",
            description = "与分页相同筛选条件，最多 20000 行；UTF-8 BOM，Excel 可直接打开。",
            operationId = "adminExportAuditLogs")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:audit:export')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) LocalDateTime startAt,
            @RequestParam(required = false) LocalDateTime endAt) {
        byte[] data = auditLogService.exportCsv(operatorId, action, resourceType, resultStatus, startAt, endAt);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-logs.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(data);
    }
}
