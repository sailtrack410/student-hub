package com.studenthub.modules.servicerequest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studenthub.exception.BusinessException;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.servicerequest.controller.dto.AdminReviewRequest;
import com.studenthub.modules.servicerequest.controller.dto.AdminServiceRequestPageQuery;
import com.studenthub.modules.servicerequest.controller.dto.AdminServiceRequestSummaryResponse;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.servicerequest.statemachine.RequestTransitions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理端服务申请审批服务：全平台数据范围分页 + 通过/驳回。
 * 通过 = SUBMITTED → PROCESSING（DISPATCH 语义：转入处理，后续由工单流转）；
 * 驳回 = SUBMITTED/PROCESSING → REJECTED。
 */
@Service
@RequiredArgsConstructor
public class AdminServiceRequestService {

    private final ServiceRequestMapper requestMapper;
    private final RequestLogRecorder requestLogRecorder;

    public PageResult<AdminServiceRequestSummaryResponse> page(AdminServiceRequestPageQuery query) {
        LambdaQueryWrapper<ServiceRequestEntity> wrapper = new LambdaQueryWrapper<ServiceRequestEntity>()
                .eq(ServiceRequestEntity::getDeleted, 0)
                .eq(query.status() != null && !query.status().isBlank(), ServiceRequestEntity::getStatus, query.status())
                .orderByDesc(ServiceRequestEntity::getSubmittedAt);
        Page<ServiceRequestEntity> page = requestMapper.selectPage(
                new Page<>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
        List<AdminServiceRequestSummaryResponse> records = page.getRecords().stream().map(this::toSummary).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Transactional(rollbackFor = Exception.class)
    public void review(AuthenticatedUser operator, Long requestId, AdminReviewRequest request) {
        ServiceRequestEntity entity = requestMapper.selectById(requestId);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_FOUND);
        }
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

    /** 审批动作字符串显式映射到状态机动作，未知动作抛 INVALID_PARAMETER。 */
    private RequestTransitions.Action toAction(String action) {
        if (AdminReviewRequest.ACTION_APPROVE.equals(action)) {
            return RequestTransitions.Action.DISPATCH;
        }
        if (AdminReviewRequest.ACTION_REJECT.equals(action)) {
            return RequestTransitions.Action.REJECT;
        }
        throw new BusinessException(ErrorCode.INVALID_PARAMETER, "未知审批动作：" + action);
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
