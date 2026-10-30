package com.studenthub.modules.servicerequest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.common.privacy.PhoneMasker;
import com.studenthub.common.util.JsonIds;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.adminscope.security.AdminDataScope;
import com.studenthub.modules.adminscope.security.AdminDataScopeService;
import com.studenthub.modules.evaluation.entity.ServiceEvaluationEntity;
import com.studenthub.modules.evaluation.mapper.ServiceEvaluationMapper;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.entity.OrganizationEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.OrganizationMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.servicecatalog.entity.ServiceCategoryEntity;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.studenthub.modules.servicecatalog.mapper.ServiceCategoryMapper;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import com.studenthub.modules.servicerequest.controller.dto.AdminRequestAttachmentResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminRequestEvaluationResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminRequestMaterialResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminRequesterResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminReviewRequest;
import com.studenthub.modules.servicerequest.controller.dto.AdminServiceRequestDetailResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminServiceRequestPageQuery;
import com.studenthub.modules.servicerequest.controller.dto.AdminServiceRequestSummaryResponse;
import com.studenthub.modules.servicerequest.controller.dto.RequestLogResponse;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.entity.ServiceRequestLogEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestLogMapper;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.servicerequest.statemachine.RequestTransitions;
import com.studenthub.modules.workorder.controller.dto.WorkOrderSummaryResponse;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端服务申请审批服务：全平台数据范围分页 + 通过/驳回 + 审批详情。
 * 通过 = SUBMITTED → PROCESSING（DISPATCH 语义：转入处理，后续由工单流转）；
 * 驳回 = SUBMITTED/PROCESSING → REJECTED。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceRequestService {

    private final ServiceRequestMapper requestMapper;
    private final ServiceRequestLogMapper requestLogMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final ServiceCategoryMapper serviceCategoryMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ServiceEvaluationMapper evaluationMapper;
    private final UserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final RequestLogRecorder requestLogRecorder;
    private final AdminCampusContextService campusContextService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final AdminDataScopeService adminDataScopeService;

    public PageResult<AdminServiceRequestSummaryResponse> page(AdminServiceRequestPageQuery query) {
        AdminDataScope dataScope = adminDataScopeService.current();
        LambdaQueryWrapper<ServiceRequestEntity> wrapper = new LambdaQueryWrapper<ServiceRequestEntity>()
                .eq(ServiceRequestEntity::getDeleted, 0)
                .eq(ServiceRequestEntity::getCampusId, campusContextService.currentCampusId())
                .eq(query.status() != null && !query.status().isBlank(), ServiceRequestEntity::getStatus, query.status())
                .and(!dataScope.unrestricted(), w -> {
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
                        // 无组织范围且不是本人范围时，管理端不返回任何申请。
                        w.eq(ServiceRequestEntity::getId, -1L);
                    }
                })
                .orderByDesc(ServiceRequestEntity::getSubmittedAt);
        Page<ServiceRequestEntity> page = requestMapper.selectPage(
                new Page<>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
        List<AdminServiceRequestSummaryResponse> records = page.getRecords().stream().map(this::toSummary).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    /**
     * 审批详情：申请人（脱敏手机号）、申请表单（图片签名地址）、材料/附件、工单、评价与时间线。
     * 仅返回当前管理端校区范围内的申请。
     */
    public AdminServiceRequestDetailResponse detail(Long requestId) {
        ServiceRequestEntity entity = requireCampusRequest(requestId);
        AdminDataScope dataScope = adminDataScopeService.current();
        if (!dataScope.unrestricted()
                && !(dataScope.hasOrganizationLimit() && dataScope.allowsOrganization(entity.getHandlerOrganizationId()))
                && !(dataScope.selfOnly() && dataScope.userId() != null
                        && dataScope.userId().equals(entity.getRequesterId()))) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_FOUND);
        }
        ServiceItemEntity item = serviceItemMapper.selectById(entity.getItemId());
        ServiceCategoryEntity category = entity.getCategoryId() == null ? null
                : serviceCategoryMapper.selectById(entity.getCategoryId());

        JsonNode formSchema = item == null ? null : parseJson(item.getFormSchema());
        JsonNode formData = enrichImageUrls(entity.getId(), parseJson(entity.getFormData()), formSchema);
        List<AdminRequestMaterialResponse> materials = extractMaterials(formData, formSchema);
        WorkOrderEntity order = workOrderMapper.findByRequestId(entity.getId());
        List<AdminRequestAttachmentResponse> attachments = extractAttachments(order);

        return new AdminServiceRequestDetailResponse(
                entity.getId(),
                entity.getRequestNo(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getItemId(),
                entity.getItemName(),
                item == null ? null : item.getIcon(),
                entity.getCategoryId(),
                category == null ? null : category.getCategoryName(),
                item == null ? null : item.getSlaHours(),
                entity.getHandlerOrganizationId(),
                organizationName(entity.getHandlerOrganizationId()),
                requester(entity.getRequesterId()),
                entity.getSubmittedAt(),
                entity.getCompletedAt(),
                entity.getRejectReason(),
                entity.getCancelReason(),
                formData,
                formSchema,
                materials,
                attachments,
                order == null ? null : WorkOrderSummaryResponse.from(order, signPhoto(order.getArrivalPhotoFileId()),
                        signPhoto(order.getCompletionPhotoFileId())),
                evaluation(entity.getId()),
                timeline(entity.getId()),
                availableActions(entity.getStatus()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void review(AuthenticatedUser operator, Long requestId, AdminReviewRequest request) {
        ServiceRequestEntity entity = requireCampusRequest(requestId);
        RequestTransitions.Status from = RequestTransitions.parse(entity.getStatus());
        RequestTransitions.Action action = toAction(request.action());
        RequestTransitions.Status to = RequestTransitions.target(action, from);

        ServiceRequestEntity update = new ServiceRequestEntity();
        update.setId(entity.getId());
        update.setStatus(to.name());
        if (to == RequestTransitions.Status.REJECTED) {
            update.setRejectReason(request.comment());
        }
        update.setVersion(entity.getVersion());
        if (requestMapper.updateById(update) == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "状态已变更，请刷新后重试");
        }

        requestLogRecorder.record(entity.getId(), null, action, from, to,
                operator.userId(), operator.displayName(), request.comment());
    }

    /**
     * 审批动作字符串显式映射到状态机动作，未知动作抛 INVALID_PARAMETER。
     */
    private RequestTransitions.Action toAction(String action) {
        if (AdminReviewRequest.ACTION_APPROVE.equals(action)) {
            return RequestTransitions.Action.DISPATCH;
        }
        if (AdminReviewRequest.ACTION_REJECT.equals(action)) {
            return RequestTransitions.Action.REJECT;
        }
        throw new BusinessException(ErrorCode.INVALID_PARAMETER, "未知审批动作：" + action);
    }

    private ServiceRequestEntity requireCampusRequest(Long requestId) {
        ServiceRequestEntity entity = requestMapper.selectById(requestId);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1
                || !campusContextService.currentCampusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_FOUND);
        }
        return entity;
    }

    private AdminRequesterResponse requester(Long userId) {
        UserEntity user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            return new AdminRequesterResponse(userId, "未知用户", null, null, null, null);
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

    /** 表单中图片字段：按 schema 顺序提取图片数组并返回已签名地址。 */
    private List<AdminRequestMaterialResponse> extractMaterials(JsonNode formData, JsonNode formSchema) {
        if (formData == null || formSchema == null || !formSchema.isArray()) {
            return List.of();
        }
        List<AdminRequestMaterialResponse> materials = new ArrayList<>();
        for (JsonNode field : formSchema) {
            String type = field.path("type").asText("input");
            if (!"image".equals(type)) {
                continue;
            }
            String key = field.path("key").asText();
            JsonNode value = formData.get(key);
            if (value == null || !value.isArray() || value.isEmpty()) {
                continue;
            }
            List<String> urls = new ArrayList<>();
            value.forEach(url -> urls.add(url.asText()));
            materials.add(new AdminRequestMaterialResponse(key, field.path("label").asText(key), type, urls));
        }
        return materials;
    }

    /** 工单现场/完工照片：签名后作为附件返回，便于审批页直接预览。 */
    private List<AdminRequestAttachmentResponse> extractAttachments(WorkOrderEntity order) {
        if (order == null) {
            return List.of();
        }
        List<AdminRequestAttachmentResponse> attachments = new ArrayList<>();
        String arrival = signPhoto(order.getArrivalPhotoFileId());
        if (arrival != null) {
            attachments.add(new AdminRequestAttachmentResponse("现场照片", "ARRIVAL_PHOTO", arrival));
        }
        List<Long> completionIds = JsonIds.parse(order.getCompletionPhotoFileIds());
        if (completionIds.isEmpty() && order.getCompletionPhotoFileId() != null) {
            completionIds = List.of(order.getCompletionPhotoFileId());
        }
        for (Long fileId : completionIds) {
            String url = signPhoto(fileId);
            if (url != null) {
                attachments.add(new AdminRequestAttachmentResponse("完工照片", "COMPLETION_PHOTO", url));
            }
        }
        return attachments;
    }

    private AdminRequestEvaluationResponse evaluation(Long requestId) {
        ServiceEvaluationEntity evaluation = evaluationMapper.selectOne(
                new LambdaQueryWrapper<ServiceEvaluationEntity>()
                        .eq(ServiceEvaluationEntity::getRequestId, requestId)
                        .last("LIMIT 1"));
        return evaluation == null ? null : AdminRequestEvaluationResponse.from(evaluation);
    }

    private List<RequestLogResponse> timeline(Long requestId) {
        return requestLogMapper.selectList(new LambdaQueryWrapper<ServiceRequestLogEntity>()
                        .eq(ServiceRequestLogEntity::getRequestId, requestId)
                        .orderByAsc(ServiceRequestLogEntity::getCreatedAt)
                        .orderByAsc(ServiceRequestLogEntity::getId))
                .stream().map(RequestLogResponse::from).toList();
    }

    /** 可执行审批动作：仅待审批可通过/驳回，处理中仅可驳回，其余状态无可执行动作。 */
    private List<String> availableActions(String status) {
        RequestTransitions.Status current;
        try {
            current = RequestTransitions.parse(status);
        } catch (BusinessException exception) {
            return List.of();
        }
        return switch (current) {
            case SUBMITTED -> List.of(AdminReviewRequest.ACTION_APPROVE, AdminReviewRequest.ACTION_REJECT);
            case PROCESSING -> List.of(AdminReviewRequest.ACTION_REJECT);
            default -> List.of();
        };
    }

    private String signPhoto(Long fileId) {
        return fileId == null ? null : fileStorageService.signViewPath(fileId);
    }

    /** 图片字段值替换为短时签名访问路径，供管理端直接预览。 */
    private JsonNode enrichImageUrls(Long requestId, JsonNode formData, JsonNode formSchema) {
        if (formData == null || formSchema == null) {
            return formData;
        }
        try {
            return fileStorageService.replaceImageValuesWithSignedUrls(formData, formSchema);
        } catch (Exception exception) {
            log.warn("管理端申请表单图片链接签名化失败, requestId={}", requestId);
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
            log.warn("申请表单 JSON 解析失败, 长度={}", raw.length());
            return null;
        }
    }

    private AdminServiceRequestSummaryResponse toSummary(ServiceRequestEntity entity) {
        return new AdminServiceRequestSummaryResponse(
                entity.getId(),
                entity.getRequestNo(),
                entity.getItemName(),
                entity.getTitle(),
                entity.getRequesterId(),
                entity.getStatus(),
                entity.getSubmittedAt(),
                entity.getCompletedAt(),
                entity.getRejectReason());
    }
}
