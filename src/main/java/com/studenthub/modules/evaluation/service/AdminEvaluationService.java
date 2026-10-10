package com.studenthub.modules.evaluation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studenthub.exception.BusinessException;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.modules.evaluation.controller.dto.AdminEvaluationPageQuery;
import com.studenthub.modules.evaluation.controller.dto.AdminEvaluationSummaryResponse;
import com.studenthub.modules.evaluation.controller.dto.AdminReplyRequest;
import com.studenthub.modules.evaluation.entity.ServiceEvaluationEntity;
import com.studenthub.modules.evaluation.mapper.ServiceEvaluationMapper;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 管理端服务评价服务：全平台分页（评分/回复状态过滤）+ 商家回复。
 * 回复后站内通知评价人。
 */
@Service
@RequiredArgsConstructor
public class AdminEvaluationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ServiceEvaluationMapper evaluationMapper;
    private final WorkOrderMapper workOrderMapper;
    private final NotificationService notificationService;

    public PageResult<AdminEvaluationSummaryResponse> page(AdminEvaluationPageQuery query) {
        LambdaQueryWrapper<ServiceEvaluationEntity> wrapper = new LambdaQueryWrapper<ServiceEvaluationEntity>()
                .eq(ServiceEvaluationEntity::getDeleted, 0)
                .eq(query.rating() != null, ServiceEvaluationEntity::getRating, query.rating());
        if (Boolean.TRUE.equals(query.replied())) {
            wrapper.isNotNull(ServiceEvaluationEntity::getReply);
        } else if (Boolean.FALSE.equals(query.replied())) {
            wrapper.isNull(ServiceEvaluationEntity::getReply);
        }
        wrapper.orderByDesc(ServiceEvaluationEntity::getCreatedAt);
        Page<ServiceEvaluationEntity> page = evaluationMapper.selectPage(
                new Page<>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
        List<AdminEvaluationSummaryResponse> records = page.getRecords().stream().map(this::toSummary).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reply(Long evaluationId, AdminReplyRequest request) {
        ServiceEvaluationEntity entity = evaluationMapper.selectById(evaluationId);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评价不存在");
        }
        if (entity.getReply() != null && !entity.getReply().isBlank()) {
            throw new BusinessException(ErrorCode.EVALUATION_DUPLICATE, "该评价已回复，不可重复回复");
        }
        entity.setReply(request.reply());
        entity.setRepliedAt(LocalDateTime.now(ZONE));
        evaluationMapper.updateById(entity);

        // 通知评价人（站内信）
        String orderTitle = null;
        if (entity.getWorkOrderId() != null) {
            WorkOrderEntity order = workOrderMapper.selectById(entity.getWorkOrderId());
            orderTitle = order == null ? null : order.getTitle();
        }
        String content = orderTitle == null
                ? "您的评价已收到回复：" + request.reply()
                : "工单「" + orderTitle + "」的评价已收到回复：" + request.reply();
        notificationService.send(new NotificationSendCommand(
                entity.getEvaluatorId(), "EVALUATION", "您的评价收到了新回复", content,
                "EVALUATION", entity.getId()));
    }

    private AdminEvaluationSummaryResponse toSummary(ServiceEvaluationEntity entity) {
        return new AdminEvaluationSummaryResponse(
                entity.getId(),
                entity.getRequestId(),
                entity.getWorkOrderId(),
                entity.getRating(),
                entity.getTags(),
                entity.getComment(),
                entity.getReply(),
                entity.getRepliedAt(),
                entity.getEvaluatorId(),
                entity.getAssigneeName(),
                entity.getCreatedAt());
    }
}
