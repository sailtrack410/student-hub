package com.studenthub.modules.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.PageResult;
import com.studenthub.modules.audit.controller.dto.AdminAuditLogResponse;
import com.studenthub.modules.audit.entity.SysAuditLogEntity;
import com.studenthub.modules.audit.mapper.SysAuditLogMapper;
import com.studenthub.modules.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_EXPORT_ROWS = 20_000;

    private final SysAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void record(Long operatorId, String operatorName, String action, String resourceType,
                       String resourceId, Object before, Object after) {
        save(operatorId, operatorName, action, resourceType, resourceId, before, after,
                SysAuditLogEntity.RESULT_SUCCESS, null);
    }

    @Override
    public void recordFailure(Long operatorId, String operatorName, String action, String resourceType,
                              String resourceId, String errorMessage, Object after) {
        save(operatorId, operatorName, action, resourceType, resourceId, null, after,
                SysAuditLogEntity.RESULT_FAILED, truncate(errorMessage, 500));
    }

    private void save(Long operatorId, String operatorName, String action, String resourceType, String resourceId,
                      Object before, Object after, String resultStatus, String errorMessage) {
        try {
            SysAuditLogEntity entity = new SysAuditLogEntity();
            entity.setOperatorId(operatorId);
            entity.setOperatorName(operatorName);
            entity.setAction(action);
            entity.setResourceType(resourceType);
            entity.setResourceId(resourceId);
            entity.setBeforeJson(toJson(before));
            entity.setAfterJson(toJson(after));
            entity.setResultStatus(resultStatus);
            entity.setErrorMessage(errorMessage);
            entity.setIp(clientIp());
            entity.setUserAgent(userAgent());
            entity.setTraceId(traceId());
            auditLogMapper.insert(entity);
        } catch (Exception exception) {
            // 审计失败不能阻断主流程，但必须留日志
            log.error("写入审计日志失败, action={}, resourceType={}, resourceId={}",
                    action, resourceType, resourceId, exception);
        }
    }

    @Override
    public PageResult<AdminAuditLogResponse> page(long pageNum, long pageSize, Long operatorId,
                                                  String action, String resourceType, String resultStatus,
                                                  LocalDateTime startAt, LocalDateTime endAt) {
        long size = Math.min(Math.max(pageSize, 1), 100);
        Page<SysAuditLogEntity> page = auditLogMapper.selectPage(new Page<>(Math.max(pageNum, 1), size),
                filter(operatorId, action, resourceType, resultStatus, startAt, endAt)
                        .orderByDesc(SysAuditLogEntity::getCreatedAt));
        List<AdminAuditLogResponse> records = page.getRecords().stream()
                .map(AdminAuditLogResponse::from).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public byte[] exportCsv(Long operatorId, String action, String resourceType, String resultStatus,
                            LocalDateTime startAt, LocalDateTime endAt) {
        List<SysAuditLogEntity> rows = auditLogMapper.selectList(
                filter(operatorId, action, resourceType, resultStatus, startAt, endAt)
                        .orderByDesc(SysAuditLogEntity::getCreatedAt)
                        .last("LIMIT " + MAX_EXPORT_ROWS));
        StringBuilder csv = new StringBuilder();
        csv.append("ID,操作时间,操作人ID,操作人,动作,资源类型,资源ID,结果,失败原因,IP,traceId,变更前JSON,变更后JSON\n");
        for (SysAuditLogEntity row : rows) {
            csv.append(csvCell(row.getId()))
                    .append(',').append(csvCell(row.getCreatedAt() == null ? null : row.getCreatedAt().format(TS)))
                    .append(',').append(csvCell(row.getOperatorId()))
                    .append(',').append(csvCell(row.getOperatorName()))
                    .append(',').append(csvCell(row.getAction()))
                    .append(',').append(csvCell(row.getResourceType()))
                    .append(',').append(csvCell(row.getResourceId()))
                    .append(',').append(csvCell(row.getResultStatus() == null
                            ? SysAuditLogEntity.RESULT_SUCCESS : row.getResultStatus()))
                    .append(',').append(csvCell(row.getErrorMessage()))
                    .append(',').append(csvCell(row.getIp()))
                    .append(',').append(csvCell(row.getTraceId()))
                    .append(',').append(csvCell(truncate(row.getBeforeJson(), 2000)))
                    .append(',').append(csvCell(truncate(row.getAfterJson(), 2000)))
                    .append('\n');
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        return withBom;
    }

    private LambdaQueryWrapper<SysAuditLogEntity> filter(Long operatorId, String action, String resourceType,
                                                         String resultStatus, LocalDateTime startAt,
                                                         LocalDateTime endAt) {
        return new LambdaQueryWrapper<SysAuditLogEntity>()
                .eq(operatorId != null, SysAuditLogEntity::getOperatorId, operatorId)
                .eq(StringUtils.hasText(action), SysAuditLogEntity::getAction, action)
                .eq(StringUtils.hasText(resourceType), SysAuditLogEntity::getResourceType, resourceType)
                .eq(StringUtils.hasText(resultStatus), SysAuditLogEntity::getResultStatus, resultStatus)
                .ge(startAt != null, SysAuditLogEntity::getCreatedAt, startAt)
                .le(endAt != null, SysAuditLogEntity::getCreatedAt, endAt);
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(value), 4000);
        } catch (Exception exception) {
            return null;
        }
    }

    private String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String userAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : truncate(request.getHeader("User-Agent"), 255);
    }

    private String traceId() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader("X-Request-Id");
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
