package com.studenthub.modules.servicerequest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.common.privacy.PhoneMasker;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.adminscope.security.AdminDataScope;
import com.studenthub.modules.adminscope.security.AdminDataScopeService;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.entity.OrganizationEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.OrganizationMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeaveDetailResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeavePageQuery;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeaveStatsResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeaveSummaryResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminRequestMaterialResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminRequesterResponse;
import com.studenthub.modules.servicerequest.controller.dto.RequestLogResponse;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.entity.ServiceRequestLogEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestLogMapper;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.servicerequest.service.LeaveRequestService;
import com.studenthub.modules.servicerequest.service.RequestLogRecorder;
import com.studenthub.modules.servicerequest.statemachine.RequestTransitions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 独立请假审批实现：复用 service_request 存储，按服务事项 item_code = AFFAIR-LEAVE 过滤。
 *
 * <p>领取假专属列（V76）为空的历史数据，接口会从 form_data 解析请假类型/起止时间作为兜底展示；
 * 审批与销假写操作会把解析值回填到专属列，保证后续筛选与统计真实可用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestServiceImpl implements LeaveRequestService {

    private static final String LEAVE_ITEM_CODE = "AFFAIR-LEAVE";
    private static final String LEAVE_ITEM_NAME_KEYWORD = "请假";

    private static final Set<String> STATUS_FILTERS = Set.of("PENDING", "APPROVED", "REJECTED", "CANCELLED");
    private static final Map<String, String> STATUS_MAP = Map.of(
            "PENDING", RequestTransitions.Status.SUBMITTED.name(),
            "APPROVED", RequestTransitions.Status.PROCESSING.name(),
            "REJECTED", RequestTransitions.Status.REJECTED.name());

    private final ServiceRequestMapper requestMapper;
    private final ServiceRequestLogMapper requestLogMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final UserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final RequestLogRecorder requestLogRecorder;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final AdminCampusContextService campusContextService;
    private final AdminDataScopeService adminDataScopeService;

    @Override
    public PageResult<AdminLeaveSummaryResponse> page(AdminLeavePageQuery query) {
        Set<Long> leaveItemIds = leaveItemIds();
        if (leaveItemIds.isEmpty()) {
            return PageResult.of(query.pageNumOrDefault(), query.pageSizeOrDefault(), 0, List.of());
        }
        AdminDataScope dataScope = adminDataScopeService.current();
        LambdaQueryWrapper<ServiceRequestEntity> wrapper = baseWrapper(leaveItemIds, dataScope);
        applyStatusFilter(wrapper, query.status());
        if (query.leaveType() != null && !query.leaveType().isBlank()) {
            wrapper.eq(ServiceRequestEntity::getLeaveType, query.leaveType().trim());
        }
        applyDateRange(wrapper, query.startDate(), query.endDate());
        applyKeyword(wrapper, query.keyword());
        wrapper.orderByDesc(ServiceRequestEntity::getSubmittedAt).orderByDesc(ServiceRequestEntity::getId);

        Page<ServiceRequestEntity> page = requestMapper.selectPage(
                new Page<>(query.pageNumOrDefault(), Math.min(query.pageSizeOrDefault(), 100)), wrapper);
        Map<Long, UserEntity> users = usersOf(page.getRecords());
        Map<Long, OrganizationEntity> organizations = organizationsOf(users);
        List<AdminLeaveSummaryResponse> records = page.getRecords().stream()
                .map(entity -> toSummary(entity, users, organizations)).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public AdminLeaveStatsResponse stats() {
        Set<Long> leaveItemIds = leaveItemIds();
        if (leaveItemIds.isEmpty()) {
            return new AdminLeaveStatsResponse(0, 0, 0, 0, 0);
        }
        AdminDataScope dataScope = adminDataScopeService.current();
        long pending = requestMapper.selectCount(baseWrapper(leaveItemIds, dataScope)
                .eq(ServiceRequestEntity::getStatus, RequestTransitions.Status.SUBMITTED.name()));
        long approved = requestMapper.selectCount(baseWrapper(leaveItemIds, dataScope)
                .in(ServiceRequestEntity::getStatus, RequestTransitions.Status.PROCESSING.name(),
                        RequestTransitions.Status.COMPLETED.name())
                .isNull(ServiceRequestEntity::getLeaveCancelAt));
        long rejected = requestMapper.selectCount(baseWrapper(leaveItemIds, dataScope)
                .eq(ServiceRequestEntity::getStatus, RequestTransitions.Status.REJECTED.name()));
        long cancelled = requestMapper.selectCount(baseWrapper(leaveItemIds, dataScope)
                .isNotNull(ServiceRequestEntity::getLeaveCancelAt));
        long total = requestMapper.selectCount(baseWrapper(leaveItemIds, dataScope));
        return new AdminLeaveStatsResponse(pending, approved, rejected, cancelled, total);
    }

    @Override
    public AdminLeaveDetailResponse detail(Long requestId) {
        ServiceRequestEntity entity = requireLeaveRequest(requestId);
        ensureScope(entity);
        ServiceItemEntity item = entity.getItemId() == null ? null
                : serviceItemMapper.selectById(entity.getItemId());
        LeaveForm form = leaveForm(entity);
        UserEntity requesterUser = entity.getRequesterId() == null ? null
                : userMapper.selectById(entity.getRequesterId());
        JsonNode formSchema = item == null ? null : parseJson(item.getFormSchema());
        JsonNode formData = enrichImages(parseJson(entity.getFormData()), formSchema);
        return new AdminLeaveDetailResponse(entity.getId(), entity.getRequestNo(), entity.getTitle(),
                entity.getItemName(), requesterResponse(requesterUser), entity.getHandlerOrganizationId(),
                organizationName(entity.getHandlerOrganizationId()), form.leaveType(), form.startAt(), form.endAt(),
                form.days(), form.reason(), entity.getStatus(), leaveStatus(entity), entity.getSubmittedAt(),
                entity.getCompletedAt(), entity.getLeaveCancelAt(), entity.getRejectReason(),
                entity.getCancelReason(), formData, formSchema, materials(formData, formSchema),
                timeline(entity.getId()), availableActions(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(AuthenticatedUser operator, Long requestId, String comment) {
        ServiceRequestEntity entity = requireLeaveRequest(requestId);
        ensureScope(entity);
        RequestTransitions.Status from = RequestTransitions.parse(entity.getStatus());
        RequestTransitions.Status to = RequestTransitions.target(RequestTransitions.Action.DISPATCH, from);
        LeaveForm form = leaveForm(entity);

        ServiceRequestEntity update = new ServiceRequestEntity();
        update.setId(entity.getId());
        update.setStatus(to.name());
        fillLeaveColumns(update, form);
        update.setVersion(entity.getVersion());
        if (requestMapper.updateById(update) == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "状态已变更，请刷新后重试");
        }
        String logComment = comment == null || comment.isBlank() ? "请假审批通过" : comment.trim();
        requestLogRecorder.record(entity.getId(), null, RequestTransitions.Action.DISPATCH, from, to,
                operator.userId(), operator.displayName(), logComment);
        notify(entity.getRequesterId(), "请假审批通过",
                "你的请假申请「" + requestTitle(entity) + "」已通过，请按请假时间安排。", entity.getId());
        log.info("请假审批通过, requestId={}, operatorId={}", requestId, operator.userId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(AuthenticatedUser operator, Long requestId, String reason) {
        ServiceRequestEntity entity = requireLeaveRequest(requestId);
        ensureScope(entity);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "驳回必须填写原因");
        }
        RequestTransitions.Status from = RequestTransitions.parse(entity.getStatus());
        RequestTransitions.Status to = RequestTransitions.target(RequestTransitions.Action.REJECT, from);
        LeaveForm form = leaveForm(entity);

        ServiceRequestEntity update = new ServiceRequestEntity();
        update.setId(entity.getId());
        update.setStatus(to.name());
        update.setRejectReason(reason.trim());
        fillLeaveColumns(update, form);
        update.setVersion(entity.getVersion());
        if (requestMapper.updateById(update) == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "状态已变更，请刷新后重试");
        }
        requestLogRecorder.record(entity.getId(), null, RequestTransitions.Action.REJECT, from, to,
                operator.userId(), operator.displayName(), reason.trim());
        notify(entity.getRequesterId(), "请假申请被驳回",
                "你的请假申请「" + requestTitle(entity) + "」被驳回：" + reason.trim(), entity.getId());
        log.info("请假审批驳回, requestId={}, operatorId={}", requestId, operator.userId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelLeave(AuthenticatedUser operator, Long requestId, String comment) {
        ServiceRequestEntity entity = requireLeaveRequest(requestId);
        ensureScope(entity);
        if (entity.getLeaveCancelAt() != null) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "该请假已销假，请勿重复操作");
        }
        RequestTransitions.Status from = RequestTransitions.parse(entity.getStatus());
        RequestTransitions.Status to = RequestTransitions.target(RequestTransitions.Action.COMPLETE, from);
        LeaveForm form = leaveForm(entity);
        LocalDateTime now = LocalDateTime.now();

        ServiceRequestEntity update = new ServiceRequestEntity();
        update.setId(entity.getId());
        update.setStatus(to.name());
        update.setCompletedAt(now);
        update.setLeaveCancelAt(now);
        update.setLeaveCancelStatus("CANCELLED");
        fillLeaveColumns(update, form);
        update.setVersion(entity.getVersion());
        if (requestMapper.updateById(update) == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "状态已变更，请刷新后重试");
        }
        String logComment = comment == null || comment.isBlank() ? "销假" : "销假：" + comment.trim();
        requestLogRecorder.record(entity.getId(), null, RequestTransitions.Action.COMPLETE, from, to,
                operator.userId(), operator.displayName(), logComment);
        notify(entity.getRequesterId(), "请假已销假",
                "你的请假申请「" + requestTitle(entity) + "」已办理销假。", entity.getId());
        log.info("请假销假, requestId={}, operatorId={}", requestId, operator.userId());
    }

    // ---------------------------------------------------------------------
    // 查询条件与数据范围
    // ---------------------------------------------------------------------

    /** 真实服务事项 code 为 AFFAIR-LEAVE（V8 种子）；找不到时按事项名称包含“请假”兜底。 */
    private Set<Long> leaveItemIds() {
        Long campusId = campusContextService.currentCampusId();
        List<ServiceItemEntity> items = serviceItemMapper.selectList(new LambdaQueryWrapper<ServiceItemEntity>()
                .eq(ServiceItemEntity::getItemCode, LEAVE_ITEM_CODE));
        if (items.isEmpty()) {
            items = serviceItemMapper.selectList(new LambdaQueryWrapper<ServiceItemEntity>()
                    .like(ServiceItemEntity::getItemName, LEAVE_ITEM_NAME_KEYWORD));
        }
        Set<Long> ids = items.stream()
                .filter(item -> item.getCampusId() == null || item.getCampusId().equals(0L)
                        || item.getCampusId().equals(campusId))
                .map(ServiceItemEntity::getId).collect(Collectors.toSet());
        return ids;
    }

    private LambdaQueryWrapper<ServiceRequestEntity> baseWrapper(Set<Long> leaveItemIds, AdminDataScope dataScope) {
        LambdaQueryWrapper<ServiceRequestEntity> wrapper = new LambdaQueryWrapper<ServiceRequestEntity>()
                .eq(ServiceRequestEntity::getDeleted, 0)
                .eq(ServiceRequestEntity::getCampusId, campusContextService.currentCampusId())
                .in(ServiceRequestEntity::getItemId, leaveItemIds);
        if (!dataScope.unrestricted()) {
            wrapper.and(w -> {
                boolean hasCondition = false;
                if (dataScope.hasOrganizationLimit()) {
                    w.in(ServiceRequestEntity::getHandlerOrganizationId, dataScope.organizationIds());
                    hasCondition = true;
                }
                if (dataScope.selfOnly() && dataScope.userId() != null) {
                    if (hasCondition) {
                        w.or();
                    }
                    w.eq(ServiceRequestEntity::getRequesterId, dataScope.userId());
                    hasCondition = true;
                }
                if (!hasCondition) {
                    w.eq(ServiceRequestEntity::getId, -1L);
                }
            });
        }
        return wrapper;
    }

    private void applyStatusFilter(LambdaQueryWrapper<ServiceRequestEntity> wrapper, String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        String normalized = status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!STATUS_FILTERS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "请假状态仅支持 PENDING/APPROVED/REJECTED/CANCELLED");
        }
        if ("CANCELLED".equals(normalized)) {
            wrapper.isNotNull(ServiceRequestEntity::getLeaveCancelAt);
            return;
        }
        if ("APPROVED".equals(normalized)) {
            wrapper.in(ServiceRequestEntity::getStatus, RequestTransitions.Status.PROCESSING.name(),
                            RequestTransitions.Status.COMPLETED.name())
                    .isNull(ServiceRequestEntity::getLeaveCancelAt);
            return;
        }
        wrapper.eq(ServiceRequestEntity::getStatus, STATUS_MAP.get(normalized));
    }

    private void applyDateRange(LambdaQueryWrapper<ServiceRequestEntity> wrapper, LocalDate start, LocalDate end) {
        if (start != null) {
            wrapper.apply("COALESCE(leave_start_at, submitted_at) >= {0}", start.atStartOfDay());
        }
        if (end != null) {
            wrapper.apply("COALESCE(leave_start_at, submitted_at) < {0}", end.plusDays(1).atStartOfDay());
        }
    }

    private void applyKeyword(LambdaQueryWrapper<ServiceRequestEntity> wrapper, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String value = keyword.trim();
        List<Long> matchedUserIds = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .like(UserEntity::getDisplayName, value).last("LIMIT 50"))
                .stream().map(UserEntity::getId).toList();
        wrapper.and(w -> {
            w.like(ServiceRequestEntity::getRequestNo, value)
                    .or().like(ServiceRequestEntity::getTitle, value);
            if (!matchedUserIds.isEmpty()) {
                w.or().in(ServiceRequestEntity::getRequesterId, matchedUserIds);
            }
        });
    }

    private ServiceRequestEntity requireLeaveRequest(Long requestId) {
        ServiceRequestEntity entity = requestMapper.selectById(requestId);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1
                || !campusContextService.currentCampusId().equals(entity.getCampusId())
                || entity.getItemId() == null || !leaveItemIds().contains(entity.getItemId())) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_FOUND, "请假申请不存在或不在当前校区");
        }
        return entity;
    }

    private void ensureScope(ServiceRequestEntity entity) {
        AdminDataScope dataScope = adminDataScopeService.current();
        if (dataScope.unrestricted()) {
            return;
        }
        boolean organizationAllowed = dataScope.hasOrganizationLimit()
                && dataScope.allowsOrganization(entity.getHandlerOrganizationId());
        boolean selfAllowed = dataScope.selfOnly() && dataScope.userId() != null
                && dataScope.userId().equals(entity.getRequesterId());
        if (!organizationAllowed && !selfAllowed) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_FOUND, "没有该请假申请的数据权限");
        }
    }

    // ---------------------------------------------------------------------
    // 请假字段解析/填充
    // ---------------------------------------------------------------------

    private LeaveForm leaveForm(ServiceRequestEntity entity) {
        JsonNode data = parseJson(entity.getFormData());
        String leaveType = entity.getLeaveType();
        LocalDateTime startAt = entity.getLeaveStartAt();
        LocalDateTime endAt = entity.getLeaveEndAt();
        BigDecimal days = entity.getLeaveDays();
        String reason = data != null && data.hasNonNull("reason") ? data.get("reason").asText() : null;
        if (data != null) {
            if (leaveType == null && data.hasNonNull("leaveType")) {
                leaveType = data.get("leaveType").asText();
            }
            if (startAt == null) {
                LocalDate start = parseDate(text(data, "startDate"));
                startAt = start == null ? null : start.atStartOfDay();
            }
            if (endAt == null) {
                LocalDate end = parseDate(text(data, "endDate"));
                endAt = end == null ? null : end.atTime(23, 59, 59);
            }
        }
        if (days == null && startAt != null && endAt != null && !endAt.isBefore(startAt)) {
            days = BigDecimal.valueOf(ChronoUnit.DAYS.between(startAt.toLocalDate(), endAt.toLocalDate()) + 1)
                    .setScale(1, RoundingMode.HALF_UP);
        }
        return new LeaveForm(leaveType, startAt, endAt, days, reason);
    }

    private void fillLeaveColumns(ServiceRequestEntity update, LeaveForm form) {
        update.setLeaveType(form.leaveType());
        update.setLeaveStartAt(form.startAt());
        update.setLeaveEndAt(form.endAt());
        update.setLeaveDays(form.days());
    }

    private String leaveStatus(ServiceRequestEntity entity) {
        if (entity.getLeaveCancelAt() != null) {
            return "CANCELLED";
        }
        return switch (entity.getStatus()) {
            case "SUBMITTED" -> "PENDING";
            case "PROCESSING" -> "APPROVED";
            case "REJECTED" -> "REJECTED";
            case "CANCELLED" -> "CANCELLED";
            case "COMPLETED" -> "APPROVED";
            default -> entity.getStatus();
        };
    }

    private List<String> availableActions(ServiceRequestEntity entity) {
        if (entity.getLeaveCancelAt() != null) {
            return List.of();
        }
        return switch (entity.getStatus()) {
            case "SUBMITTED" -> List.of("APPROVE", "REJECT");
            case "PROCESSING" -> List.of("CANCEL");
            default -> List.of();
        };
    }

    // ---------------------------------------------------------------------
    // DTO 组装
    // ---------------------------------------------------------------------

    private AdminLeaveSummaryResponse toSummary(ServiceRequestEntity entity, Map<Long, UserEntity> users,
                                                Map<Long, OrganizationEntity> organizations) {
        LeaveForm form = leaveForm(entity);
        UserEntity requester = entity.getRequesterId() == null ? null : users.get(entity.getRequesterId());
        String organizationName = requester == null || requester.getDefaultOrganizationId() == null ? null
                : organizations.get(requester.getDefaultOrganizationId()) == null ? null
                : organizations.get(requester.getDefaultOrganizationId()).getOrganizationName();
        return new AdminLeaveSummaryResponse(entity.getId(), entity.getRequestNo(), entity.getRequesterId(),
                requester == null ? null : requester.getDisplayName(), organizationName, form.leaveType(),
                form.startAt(), form.endAt(), form.days(), entity.getStatus(), leaveStatus(entity),
                entity.getSubmittedAt(), entity.getCompletedAt(), entity.getLeaveCancelAt(), entity.getRejectReason());
    }

    private Map<Long, UserEntity> usersOf(List<ServiceRequestEntity> entities) {
        Set<Long> ids = entities.stream().map(ServiceRequestEntity::getRequesterId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserEntity> map = new HashMap<>();
        userMapper.selectBatchIds(ids).forEach(user -> map.put(user.getId(), user));
        return map;
    }

    private Map<Long, OrganizationEntity> organizationsOf(Map<Long, UserEntity> users) {
        Set<Long> ids = users.values().stream().map(UserEntity::getDefaultOrganizationId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, OrganizationEntity> map = new HashMap<>();
        organizationMapper.selectBatchIds(ids).forEach(org -> map.put(org.getId(), org));
        return map;
    }

    private AdminRequesterResponse requesterResponse(UserEntity user) {
        if (user == null) {
            return new AdminRequesterResponse(null, "未知用户", null, null, null, null);
        }
        return new AdminRequesterResponse(user.getId(), user.getDisplayName(),
                PhoneMasker.mask(user.getPhone()), user.getDefaultOrganizationId(),
                organizationName(user.getDefaultOrganizationId()), user.getIdentityType());
    }

    private String organizationName(Long organizationId) {
        if (organizationId == null) {
            return null;
        }
        OrganizationEntity organization = organizationMapper.selectById(organizationId);
        return organization == null ? null : organization.getOrganizationName();
    }

    private List<RequestLogResponse> timeline(Long requestId) {
        return requestLogMapper.selectList(new LambdaQueryWrapper<ServiceRequestLogEntity>()
                        .eq(ServiceRequestLogEntity::getRequestId, requestId)
                        .orderByAsc(ServiceRequestLogEntity::getCreatedAt)
                        .orderByAsc(ServiceRequestLogEntity::getId))
                .stream().map(RequestLogResponse::from).toList();
    }

    private List<AdminRequestMaterialResponse> materials(JsonNode formData, JsonNode formSchema) {
        if (formData == null || formSchema == null || !formSchema.isArray()) {
            return List.of();
        }
        List<AdminRequestMaterialResponse> materials = new ArrayList<>();
        for (JsonNode field : formSchema) {
            if (!"image".equals(field.path("type").asText("input"))) {
                continue;
            }
            String key = field.path("key").asText();
            JsonNode value = formData.get(key);
            if (value == null || !value.isArray() || value.isEmpty()) {
                continue;
            }
            List<String> urls = new ArrayList<>();
            value.forEach(url -> urls.add(url.asText()));
            materials.add(new AdminRequestMaterialResponse(key, field.path("label").asText(key), "image", urls));
        }
        return materials;
    }

    private JsonNode enrichImages(JsonNode formData, JsonNode formSchema) {
        if (formData == null || formSchema == null) {
            return formData;
        }
        try {
            return fileStorageService.replaceImageValuesWithSignedUrls(formData, formSchema);
        } catch (Exception exception) {
            log.warn("请假表单图片链接签名化失败: {}", exception.getMessage());
            return formData;
        }
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception exception) {
            log.warn("请假表单 JSON 解析失败, 长度={}", raw.length());
            return null;
        }
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
        } catch (Exception exception) {
            return null;
        }
    }

    private String requestTitle(ServiceRequestEntity entity) {
        return entity.getTitle() == null || entity.getTitle().isBlank() ? entity.getRequestNo() : entity.getTitle();
    }

    private void notify(Long userId, String title, String content, Long requestId) {
        if (userId == null) {
            return;
        }
        try {
            notificationService.send(new NotificationSendCommand(userId, "REQUEST", title, content,
                    "SERVICE_REQUEST", requestId));
        } catch (Exception exception) {
            log.warn("请假审批通知发送失败, requestId={}", requestId, exception);
        }
    }

    private record LeaveForm(String leaveType, LocalDateTime startAt, LocalDateTime endAt,
                             BigDecimal days, String reason) {
    }
}
