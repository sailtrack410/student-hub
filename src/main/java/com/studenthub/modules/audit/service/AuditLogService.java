package com.studenthub.modules.audit.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.audit.controller.dto.AdminAuditLogResponse;

import java.time.LocalDateTime;

public interface AuditLogService {

    void record(Long operatorId, String operatorName, String action, String resourceType,
                String resourceId, Object before, Object after);

    /** 记录失败写操作（V71）：resultStatus=FAILED，保留错误摘要与请求参数。 */
    void recordFailure(Long operatorId, String operatorName, String action, String resourceType,
                       String resourceId, String errorMessage, Object after);

    PageResult<AdminAuditLogResponse> page(long pageNum, long pageSize, Long operatorId,
                                           String action, String resourceType, String resultStatus,
                                           LocalDateTime startAt, LocalDateTime endAt);

    /** 全量导出（CSV，UTF-8 BOM）：按与分页相同的筛选条件，最多 20000 行。 */
    byte[] exportCsv(Long operatorId, String action, String resourceType, String resultStatus,
                     LocalDateTime startAt, LocalDateTime endAt);
}
