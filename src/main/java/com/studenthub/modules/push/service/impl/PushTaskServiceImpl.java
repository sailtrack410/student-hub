package com.studenthub.modules.push.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.entity.UserRoleEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.mapper.UserRoleMapper;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.push.controller.dto.PushDeliveryResponse;
import com.studenthub.modules.push.controller.dto.PushStatsResponse;
import com.studenthub.modules.push.controller.dto.PushTaskPageQuery;
import com.studenthub.modules.push.controller.dto.PushTaskRequest;
import com.studenthub.modules.push.controller.dto.PushTaskResponse;
import com.studenthub.modules.push.entity.PushTaskDeliveryEntity;
import com.studenthub.modules.push.entity.PushTaskEntity;
import com.studenthub.modules.push.mapper.PushTaskDeliveryMapper;
import com.studenthub.modules.push.mapper.PushTaskMapper;
import com.studenthub.modules.push.provider.PushChannels;
import com.studenthub.modules.push.provider.PushDeliveryResult;
import com.studenthub.modules.push.provider.PushDeliveryStatus;
import com.studenthub.modules.push.provider.PushMessage;
import com.studenthub.modules.push.provider.PushProviderRegistry;
import com.studenthub.modules.push.service.PushTaskService;
import com.studenthub.modules.push.service.PushTemplateRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 推送任务实现。
 *
 * <p>关键约束（真实性）：
 * <ul>
 *   <li>渠道结果只来自 {@link PushProviderRegistry}，未配置渠道写 NOT_CONFIGURED，不计成功/失败；</li>
 *   <li>站内通知（LOG/WS 渠道时）逐用户真实落库，App 消息中心可见；</li>
 *   <li>频控按「同一用户窗口内已成功投递条数」跨任务统计，命中写 SKIPPED 并记录原因；</li>
 *   <li>计数在每次发送/重试后从投递明细重算，不做增量估算；</li>
 *   <li>点击由 App 回调 {@link #recordClick} 写入，未接入时点击数为 0（不伪造）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushTaskServiceImpl implements PushTaskService {

    private static final int MAX_PAGE_SIZE = 50;
    /** 单次发送的最大接收人数：防止一次任务把库/内存打满（超出部分不会静默截断，写入 lastError）。 */
    private static final int MAX_AUDIENCE = 2000;
    private static final int NOTIFICATION_TITLE_LIMIT = 120;
    private static final int NOTIFICATION_CONTENT_LIMIT = 500;

    private static final Set<String> AUDIENCE_TYPES = Set.of("ALL", "CAMPUS", "ROLE", "ORGANIZATION", "USER");
    private static final Set<String> SCHEDULE_TYPES = Set.of("NOW", "SCHEDULED");
    private static final Set<String> ACTIONS = Set.of("DRAFT", "SEND", "SCHEDULE");
    private static final Set<String> IN_APP_CHANNELS = Set.of(PushChannels.LOG, PushChannels.WS);

    private final PushTaskMapper taskMapper;
    private final PushTaskDeliveryMapper deliveryMapper;
    private final PushProviderRegistry providerRegistry;
    private final NotificationService notificationService;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final AdminCampusContextService campusContextService;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ 查询

    @Override
    public PageResult<PushTaskResponse> page(PushTaskPageQuery query) {
        int current = Math.max(1, query.pageNumOrDefault());
        int size = Math.max(1, Math.min(query.pageSizeOrDefault(), MAX_PAGE_SIZE));
        String status = normalizeEnum(query.status());
        LambdaQueryWrapper<PushTaskEntity> wrapper = new LambdaQueryWrapper<PushTaskEntity>()
                .eq(PushTaskEntity::getCampusId, campusId())
                .eq(StringUtils.hasText(status), PushTaskEntity::getStatus, status)
                .and(StringUtils.hasText(query.keyword()), w -> {
                    String value = query.keyword().trim();
                    w.like(PushTaskEntity::getTaskName, value)
                            .or().like(PushTaskEntity::getTitle, value);
                })
                .orderByDesc(PushTaskEntity::getId);
        Page<PushTaskEntity> page = taskMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(PushTaskResponse::from).toList());
    }

    @Override
    public PushTaskResponse detail(Long id) {
        return PushTaskResponse.from(requireTask(id));
    }

    @Override
    public PageResult<PushDeliveryResponse> pageDeliveries(Long taskId, Integer pageNum, Integer pageSize, String status) {
        PushTaskEntity task = requireTask(taskId);
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        String normalizedStatus = normalizeEnum(status);
        LambdaQueryWrapper<PushTaskDeliveryEntity> wrapper = new LambdaQueryWrapper<PushTaskDeliveryEntity>()
                .eq(PushTaskDeliveryEntity::getTaskId, task.getId())
                .eq(StringUtils.hasText(normalizedStatus), PushTaskDeliveryEntity::getStatus, normalizedStatus)
                .orderByAsc(PushTaskDeliveryEntity::getChannel)
                .orderByAsc(PushTaskDeliveryEntity::getUserId);
        Page<PushTaskDeliveryEntity> page = deliveryMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(PushDeliveryResponse::from).toList());
    }

    // ------------------------------------------------------------------ 写入

    @Override
    public PushTaskResponse create(PushTaskRequest request) {
        PushTaskEntity task = new PushTaskEntity();
        task.setCampusId(campusId());
        applyRequest(task, request, true);
        String action = normalizeEnum(request.action());
        if (action == null || !ACTIONS.contains(action)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "创建动作仅支持 DRAFT/SEND/SCHEDULE");
        }
        // 发送前先做渲染与人群校验：失败直接 400，不留下脏任务与半截数据。
        RenderResult rendered = renderTask(task);
        if (rendered.missing().length > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板变量缺失：" + rendered.missingList());
        }
        List<Long> audience = resolveAudience(task);
        if (audience.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "所选人群没有可接收的启用用户");
        }
        if (audience.size() >= MAX_AUDIENCE) {
            task.setLastError("人群超过单次发送上限 " + MAX_AUDIENCE + " 人，已按上限截断，请拆分任务");
        }
        task.setStatus("DRAFT");
        if ("SCHEDULE".equals(action)) {
            if (task.getScheduledAt() == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "定时发送必须选择发送时间");
            }
            if (task.getScheduledAt().isBefore(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "定时发送时间必须晚于当前时间");
            }
            task.setStatus("SCHEDULED");
        }
        taskMapper.insert(task);
        log.info("推送任务已创建, id={}, action={}, audience={}", task.getId(), action, audience.size());
        if ("SEND".equals(action)) {
            execute(task, audience);
            return PushTaskResponse.from(taskMapper.selectById(task.getId()));
        }
        return PushTaskResponse.from(taskMapper.selectById(task.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PushTaskResponse update(Long id, PushTaskRequest request) {
        PushTaskEntity task = requireTask(id);
        if (!"DRAFT".equals(task.getStatus()) && !"SCHEDULED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "仅草稿或定时待发任务可编辑");
        }
        String expectedStatus = task.getStatus();
        applyRequest(task, request, false);
        RenderResult rendered = renderTask(task);
        if (rendered.missing().length > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板变量缺失：" + rendered.missingList());
        }
        if (taskMapper.update(task, new LambdaUpdateWrapper<PushTaskEntity>()
                .eq(PushTaskEntity::getId, id).eq(PushTaskEntity::getStatus, expectedStatus)) != 1) {
            throw stateChanged();
        }
        return PushTaskResponse.from(taskMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PushTaskEntity task = requireTask(id);
        if ("SENDING".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "任务正在发送中，不能删除");
        }
        if (taskMapper.deleteIdle(task) != 1) throw stateChanged();
        deliveryMapper.delete(new LambdaQueryWrapper<PushTaskDeliveryEntity>()
                .eq(PushTaskDeliveryEntity::getTaskId, task.getId()));
        log.info("推送任务已删除, id={}", id);
    }

    @Override
    public PushTaskResponse sendNow(Long id) {
        PushTaskEntity task = requireTask(id);
        if (!Set.of("DRAFT", "SCHEDULED").contains(task.getStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "仅草稿或定时待发任务可发送；失败投递请使用重试");
        }
        RenderResult rendered = renderTask(task);
        if (rendered.missing().length > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板变量缺失：" + rendered.missingList());
        }
        List<Long> audience = resolveAudience(task);
        if (audience.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "所选人群没有可接收的启用用户");
        }
        execute(task, audience);
        return PushTaskResponse.from(taskMapper.selectById(task.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PushTaskResponse cancel(Long id) {
        PushTaskEntity task = requireTask(id);
        if (!"DRAFT".equals(task.getStatus()) && !"SCHEDULED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "仅草稿或定时待发任务可取消");
        }
        String expectedStatus = task.getStatus();
        task.setStatus("CANCELLED");
        task.setFinishedAt(LocalDateTime.now());
        if (taskMapper.update(task, new LambdaUpdateWrapper<PushTaskEntity>()
                .eq(PushTaskEntity::getId, id).eq(PushTaskEntity::getStatus, expectedStatus)) != 1) throw stateChanged();
        return PushTaskResponse.from(taskMapper.selectById(id));
    }

    @Override
    public PushTaskResponse retry(Long id) {
        PushTaskEntity task = requireTask(id);
        if (!Set.of("FAILED", "PARTIAL").contains(task.getStatus())) throw stateChanged();
        List<PushTaskDeliveryEntity> retryable = deliveryMapper.selectList(
                new LambdaQueryWrapper<PushTaskDeliveryEntity>()
                        .eq(PushTaskDeliveryEntity::getTaskId, task.getId())
                        .in(PushTaskDeliveryEntity::getStatus, PushDeliveryStatus.FAILED, PushDeliveryStatus.NOT_CONFIGURED));
        if (retryable.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "没有可重试的失败/未配置投递记录");
        }
        RenderResult rendered = renderTask(task);
        if (rendered.missing().length > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板变量缺失：" + rendered.missingList());
        }
        claim(task);
        task.setRetryCount(value(task.getRetryCount()) + 1);
        try {
        for (PushTaskDeliveryEntity delivery : retryable) {
            requireOwnership(task);
            delivery.setStatus(PushDeliveryStatus.PENDING);
            delivery.setAttempts(value(delivery.getAttempts()) + 1);
            deliveryMapper.updateById(delivery);
            PushDeliveryResult result = providerRegistry.send(delivery.getChannel(), new PushMessage(
                    task.getId(), delivery.getUserId(), rendered.title(), rendered.content(),
                    Map.of("notificationType", "PUSH_TASK", "taskId", task.getId())));
            delivery.setStatus(result.status());
            delivery.setProviderMessage(truncate(result.message(), 200));
            delivery.setErrorCode(result.errorCode());
            delivery.setErrorMessage(result.errorCode() == null ? null : truncate(result.message(), 500));
            if (PushDeliveryStatus.isSuccess(result.status())) {
                delivery.setSentAt(LocalDateTime.now());
                if (PushDeliveryStatus.isDelivered(result.status())) {
                    delivery.setDeliveredAt(LocalDateTime.now());
                }
            }
            deliveryMapper.updateById(delivery);
        }
        refreshCounters(task);
        task.setStatus(resolveFinalStatus(task));
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.finishOwned(task);
        } catch (RuntimeException exception) {
            refreshCounters(task);
            task.setStatus("FAILED");
            task.setFinishedAt(LocalDateTime.now());
            task.setLastError("重试异常；PENDING 投递需核对渠道回执：" + truncate(exception.getMessage(), 250));
            taskMapper.finishOwned(task);
        }
        log.info("推送任务已重试, id={}, retried={}", task.getId(), retryable.size());
        return PushTaskResponse.from(taskMapper.selectById(id));
    }

    // ------------------------------------------------------------------ 定时

    @Override
    public int dispatchDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        // Recovered tasks are never sent automatically. Ambiguous PENDING attempts stay out of retry selection.
        taskMapper.recoverInterrupted(now.minusMinutes(30));
        List<PushTaskEntity> due = taskMapper.selectList(new LambdaQueryWrapper<PushTaskEntity>()
                .eq(PushTaskEntity::getStatus, "SCHEDULED")
                .isNotNull(PushTaskEntity::getScheduledAt)
                .le(PushTaskEntity::getScheduledAt, now)
                .last("LIMIT 20"));
        int handled = 0;
        for (PushTaskEntity task : due) {
            try {
                List<Long> audience = resolveAudience(task);
                execute(task, audience);
                handled++;
            } catch (RuntimeException exception) {
                log.warn("定时推送任务未执行（可能已由其他节点领取）, taskId={}: {}", task.getId(), exception.getMessage());
            }
        }
        return handled;
    }

    // ------------------------------------------------------------------ 点击

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean recordClick(Long taskId, Long userId, String channel) {
        if (taskId == null || userId == null || !StringUtils.hasText(channel)) {
            return false;
        }
        PushTaskDeliveryEntity delivery = deliveryMapper.selectOne(
                new LambdaQueryWrapper<PushTaskDeliveryEntity>()
                        .eq(PushTaskDeliveryEntity::getTaskId, taskId)
                        .eq(PushTaskDeliveryEntity::getUserId, userId)
                        .eq(PushTaskDeliveryEntity::getChannel, channel.trim().toUpperCase(Locale.ROOT))
                        .last("LIMIT 1"));
        if (delivery == null) {
            return false;
        }
        if (PushDeliveryStatus.CLICKED.equals(delivery.getStatus())) {
            return true;
        }
        delivery.setStatus(PushDeliveryStatus.CLICKED);
        delivery.setClickedAt(LocalDateTime.now());
        deliveryMapper.updateById(delivery);
        PushTaskEntity task = taskMapper.selectById(taskId);
        if (task != null) {
            refreshCounters(task);
            taskMapper.update(null, new LambdaUpdateWrapper<PushTaskEntity>()
                    .eq(PushTaskEntity::getId, taskId)
                    .set(PushTaskEntity::getClickedCount, task.getClickedCount())
                    .set(PushTaskEntity::getDeliveredCount, task.getDeliveredCount()));
        }
        return true;
    }

    // ------------------------------------------------------------------ 统计

    @Override
    public PushStatsResponse stats(LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime start = from == null ? end.minusDays(7) : from;
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "统计起始时间不能晚于结束时间");
        }
        Long campusId = campusId();
        List<Map<String, Object>> rows = deliveryMapper.countByChannelStatus(campusId, start, end);
        Map<String, long[]> byChannel = new java.util.LinkedHashMap<>();
        long total = 0;
        long success = 0;
        long failed = 0;
        long delivered = 0;
        long clicked = 0;
        long notConfigured = 0;
        long skipped = 0;
        for (Map<String, Object> row : rows) {
            String channel = stringValue(row, "channel");
            String status = stringValue(row, "status");
            long count = longValue(row, "cnt");
            long[] stat = byChannel.computeIfAbsent(channel, key -> new long[7]);
            stat[0] += count;
            total += count;
            if (PushDeliveryStatus.isSuccess(status)) {
                success += count;
                stat[1] += count;
            } else if (PushDeliveryStatus.FAILED.equals(status) || PushDeliveryStatus.PENDING.equals(status)) {
                failed += count;
                stat[2] += count;
            } else if (PushDeliveryStatus.NOT_CONFIGURED.equals(status)) {
                notConfigured += count;
                stat[5] += count;
            } else if (PushDeliveryStatus.SKIPPED.equals(status)) {
                skipped += count;
                stat[6] += count;
            }
            if (PushDeliveryStatus.isDelivered(status)) {
                delivered += count;
                stat[3] += count;
            }
            if (PushDeliveryStatus.CLICKED.equals(status)) {
                clicked += count;
                stat[4] += count;
            }
        }
        List<PushStatsResponse.ChannelStat> channels = new ArrayList<>();
        byChannel.forEach((channel, stat) -> channels.add(new PushStatsResponse.ChannelStat(
                channel, stat[0], stat[1], stat[2], stat[3], stat[4], stat[5], stat[6])));
        List<PushStatsResponse.ChannelCapability> capabilities = providerRegistry.statuses().stream()
                .map(status -> new PushStatsResponse.ChannelCapability(
                        status.channel(), status.configured(), status.reason()))
                .toList();
        return new PushStatsResponse(
                start.toString(), end.toString(), total, success, failed, delivered, clicked,
                notConfigured, skipped,
                success == 0 ? null : round((double) delivered / success),
                delivered == 0 ? null : round((double) clicked / delivered),
                channels, capabilities);
    }

    // ------------------------------------------------------------------ 执行内核

    /** 执行发送：所有异常转为任务 FAILED + lastError，不向调用方抛（保证状态可查）。 */
    private void execute(PushTaskEntity task, List<Long> audience) {
        claim(task);
        RenderResult rendered = renderTask(task);
        if (rendered.missing().length > 0) {
            task.setStatus("FAILED");
            task.setLastError("模板变量缺失：" + rendered.missingList());
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.finishOwned(task);
            return;
        }
        List<String> channels = PushChannels.parse(task.getChannels());
        if (channels.isEmpty()) {
            channels = List.of(PushChannels.LOG);
        }

        int createdNotifications = 0;
        try {
            boolean inApp = channels.stream().anyMatch(IN_APP_CHANNELS::contains);
            for (Long userId : audience) {
                boolean throttled = isThrottled(task, userId);
                for (String channel : channels) {
                    requireOwnership(task);
                    PushTaskDeliveryEntity delivery = new PushTaskDeliveryEntity();
                    delivery.setTaskId(task.getId());
                    delivery.setCampusId(task.getCampusId());
                    delivery.setUserId(userId);
                    delivery.setChannel(channel);
                    delivery.setAttempts(1);
                    if (throttled) {
                        delivery.setStatus(PushDeliveryStatus.SKIPPED);
                        delivery.setProviderMessage("频控命中：窗口内已接收 " + value(task.getFrequencyLimit()) + " 次");
                        delivery.setErrorCode("PUSH_FREQUENCY_LIMIT");
                        deliveryMapper.insert(delivery);
                        continue;
                    }
                    delivery.setStatus(PushDeliveryStatus.PENDING);
                    deliveryMapper.insert(delivery);
                    PushDeliveryResult result = providerRegistry.send(channel, new PushMessage(
                            task.getId(), userId, rendered.title(), rendered.content(),
                            Map.of("notificationType", "PUSH_TASK", "taskId", task.getId())));
                    delivery.setStatus(result.status());
                    delivery.setProviderMessage(truncate(result.message(), 200));
                    delivery.setErrorCode(result.errorCode());
                    delivery.setErrorMessage(result.errorCode() == null ? null : truncate(result.message(), 500));
                    if (PushDeliveryStatus.isSuccess(result.status())) {
                        delivery.setSentAt(LocalDateTime.now());
                        if (PushDeliveryStatus.isDelivered(result.status())) {
                            delivery.setDeliveredAt(LocalDateTime.now());
                        }
                    }
                    deliveryMapper.updateById(delivery);
                }
                if (inApp && !throttled) {
                    requireOwnership(task);
                    // 站内通知必达路径：真实落库，App 消息中心可读（与 WebSocket 事件互补）。
                    notificationService.send(new NotificationSendCommand(
                            userId, "PUSH_TASK",
                            truncate(rendered.title(), NOTIFICATION_TITLE_LIMIT),
                            truncate(rendered.content(), NOTIFICATION_CONTENT_LIMIT),
                            "PUSH_TASK", task.getId()));
                    createdNotifications++;
                }
            }
            refreshCounters(task);
            task.setStatus(resolveFinalStatus(task));
            task.setFinishedAt(LocalDateTime.now());
            if (task.getLastError() == null && audience.size() >= MAX_AUDIENCE) {
                task.setLastError("人群超过单次上限 " + MAX_AUDIENCE + " 人，已截断");
            }
            taskMapper.finishOwned(task);
            log.info("推送任务发送完成, id={}, audience={}, notifications={}, status={}",
                    task.getId(), audience.size(), createdNotifications, task.getStatus());
        } catch (RuntimeException exception) {
            log.error("推送任务发送异常, taskId={}", task.getId(), exception);
            refreshCounters(task);
            task.setStatus("FAILED");
            task.setLastError("发送异常：" + truncate(exception.getMessage(), 300));
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.finishOwned(task);
        }
    }

    private void claim(PushTaskEntity task) {
        String token = java.util.UUID.randomUUID().toString();
        if (taskMapper.claim(task, token) != 1) throw stateChanged();
        task.setExecutionToken(token);
        task.setStatus("SENDING");
        task.setStartedAt(LocalDateTime.now());
        task.setFinishedAt(null);
        task.setLastError(null);
    }

    private BusinessException stateChanged() {
        return new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "任务状态已变化，请刷新后重试");
    }

    private void requireOwnership(PushTaskEntity task) {
        if (taskMapper.touchOwned(task) != 1) throw stateChanged();
    }

    /** 频控：跨任务统计该用户在窗口内已成功投递的条数。 */
    private boolean isThrottled(PushTaskEntity task, Long userId) {
        int limit = value(task.getFrequencyLimit());
        int windowHours = value(task.getFrequencyWindowHours());
        if (limit <= 0 || windowHours <= 0) {
            return false;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(windowHours);
        Long count = deliveryMapper.selectCount(new LambdaQueryWrapper<PushTaskDeliveryEntity>()
                .eq(PushTaskDeliveryEntity::getUserId, userId)
                .ge(PushTaskDeliveryEntity::getSentAt, cutoff)
                .in(PushTaskDeliveryEntity::getStatus,
                        PushDeliveryStatus.SENT, PushDeliveryStatus.DELIVERED, PushDeliveryStatus.CLICKED));
        return count != null && count >= limit;
    }

    /** 从投递明细重算任务计数（不做增量估算）。 */
    private void refreshCounters(PushTaskEntity task) {
        List<PushTaskDeliveryEntity> deliveries = deliveryMapper.selectList(
                new LambdaQueryWrapper<PushTaskDeliveryEntity>()
                        .select(PushTaskDeliveryEntity::getId, PushTaskDeliveryEntity::getStatus)
                        .eq(PushTaskDeliveryEntity::getTaskId, task.getId()));
        int total = deliveries.size();
        int success = 0;
        int failed = 0;
        int delivered = 0;
        int clicked = 0;
        int skipped = 0;
        int notConfigured = 0;
        for (PushTaskDeliveryEntity delivery : deliveries) {
            String status = delivery.getStatus();
            if (PushDeliveryStatus.isSuccess(status)) {
                success++;
            } else if (PushDeliveryStatus.FAILED.equals(status) || PushDeliveryStatus.PENDING.equals(status)) {
                failed++;
            } else if (PushDeliveryStatus.NOT_CONFIGURED.equals(status)) {
                notConfigured++;
            } else if (PushDeliveryStatus.SKIPPED.equals(status)) {
                skipped++;
            }
            if (PushDeliveryStatus.isDelivered(status)) {
                delivered++;
            }
            if (PushDeliveryStatus.CLICKED.equals(status)) {
                clicked++;
            }
        }
        task.setTotalCount(total);
        task.setSuccessCount(success);
        task.setFailedCount(failed);
        task.setDeliveredCount(delivered);
        task.setClickedCount(clicked);
        task.setSkippedCount(skipped);
        task.setNotConfiguredCount(notConfigured);
    }

    private String resolveFinalStatus(PushTaskEntity task) {
        int total = value(task.getTotalCount());
        int success = value(task.getSuccessCount());
        int failed = value(task.getFailedCount());
        int notConfigured = value(task.getNotConfiguredCount());
        int skipped = value(task.getSkippedCount());
        if (total == 0) {
            return "FAILED";
        }
        if (failed == 0 && notConfigured == 0) {
            return "SENT";
        }
        if (success > 0) {
            return "PARTIAL";
        }
        if (skipped > 0 && failed == 0) {
            return "SENT";
        }
        return "FAILED";
    }

    // ------------------------------------------------------------------ 辅助

    private void applyRequest(PushTaskEntity task, PushTaskRequest request, boolean creating) {
        String audienceType = normalizeEnum(request.audienceType());
        if (audienceType == null || !AUDIENCE_TYPES.contains(audienceType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "人群类型仅支持 ALL/CAMPUS/ROLE/ORGANIZATION/USER");
        }
        if (!"ALL".equals(audienceType) && !"CAMPUS".equals(audienceType) && !StringUtils.hasText(request.audienceValue())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "所选人群类型必须提供人群参数");
        }
        String scheduleType = normalizeEnum(request.scheduleType());
        if (scheduleType == null || !SCHEDULE_TYPES.contains(scheduleType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "发送时机仅支持 NOW/SCHEDULED");
        }
        if ("SCHEDULED".equals(scheduleType) && request.scheduledAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "定时发送必须选择发送时间");
        }
        List<String> channels;
        try {
            channels = request.channels() == null || request.channels().isEmpty()
                    ? List.of(PushChannels.LOG)
                    : PushChannels.parseAndValidate(String.join(",", request.channels()));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, exception.getMessage());
        }
        if (channels.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "至少选择一个推送渠道");
        }
        task.setTaskName(request.taskName().trim());
        task.setTemplateId(request.templateId());
        task.setTitle(request.title());
        task.setContent(request.content());
        task.setVariables(toJson(request.variables()));
        task.setAudienceType(audienceType);
        task.setAudienceValue("ALL".equals(audienceType) || "CAMPUS".equals(audienceType)
                ? null : request.audienceValue().trim());
        task.setChannels(String.join(",", channels));
        task.setScheduleType(scheduleType);
        task.setScheduledAt("SCHEDULED".equals(scheduleType) ? request.scheduledAt() : null);
        task.setFrequencyLimit(request.frequencyLimit() == null ? 3 : request.frequencyLimit());
        task.setFrequencyWindowHours(request.frequencyWindowHours() == null ? 24 : request.frequencyWindowHours());
        task.setRemark(request.remark());
        if (creating) {
            task.setStatus("DRAFT");
            task.setTotalCount(0);
            task.setSuccessCount(0);
            task.setFailedCount(0);
            task.setDeliveredCount(0);
            task.setClickedCount(0);
            task.setSkippedCount(0);
            task.setNotConfiguredCount(0);
            task.setRetryCount(0);
        }
    }

    private record RenderResult(String title, String content, String[] missing) {
        String missingList() {
            return String.join(", ", missing);
        }
    }

    private RenderResult renderTask(PushTaskEntity task) {
        Map<String, String> variables = fromJson(task.getVariables());
        PushTemplateRenderer.RenderResult titleRendered = PushTemplateRenderer.render(task.getTitle(), variables);
        PushTemplateRenderer.RenderResult contentRendered = PushTemplateRenderer.render(task.getContent(), variables);
        java.util.LinkedHashSet<String> missing = new java.util.LinkedHashSet<>(titleRendered.missing());
        missing.addAll(contentRendered.missing());
        return new RenderResult(titleRendered.text(), contentRendered.text(), missing.toArray(new String[0]));
    }

    private List<Long> resolveAudience(PushTaskEntity task) {
        Long campusId = task.getCampusId();
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .select(UserEntity::getId)
                .eq(UserEntity::getCampusId, campusId)
                .eq(UserEntity::getStatus, "ENABLED")
                .orderByAsc(UserEntity::getId)
                .last("LIMIT " + MAX_AUDIENCE);
        String type = task.getAudienceType();
        if ("ROLE".equals(type)) {
            Long roleId = parseId(task.getAudienceValue(), "角色");
            List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                            .eq(UserRoleEntity::getRoleId, roleId))
                    .stream().map(UserRoleEntity::getUserId).distinct().toList();
            if (userIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(UserEntity::getId, userIds);
        } else if ("ORGANIZATION".equals(type)) {
            Long organizationId = parseId(task.getAudienceValue(), "组织");
            wrapper.eq(UserEntity::getDefaultOrganizationId, organizationId);
        } else if ("USER".equals(type)) {
            List<Long> userIds = parseIds(task.getAudienceValue());
            if (userIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(UserEntity::getId, userIds);
        }
        return userMapper.selectList(wrapper).stream().map(UserEntity::getId).toList();
    }

    private Long parseId(String raw, String label) {
        try {
            return Long.parseLong(raw == null ? "" : raw.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "人群参数中的" + label + " ID 不合法");
        }
    }

    private List<Long> parseIds(String raw) {
        List<Long> result = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return result;
        }
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            result.add(parseId(value, "用户"));
        }
        return result;
    }

    private PushTaskEntity requireTask(Long id) {
        PushTaskEntity task = id == null ? null : taskMapper.selectById(id);
        if (task == null || !campusId().equals(task.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "推送任务不存在");
        }
        return task;
    }

    private Long campusId() {
        return campusContextService.currentCampusId();
    }

    private Map<String, String> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException exception) {
            log.warn("推送任务变量解析失败，按空变量处理");
            return Map.of();
        }
    }

    private String toJson(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板变量格式不合法");
        }
    }

    private String stringValue(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue() == null ? "" : entry.getValue().toString();
            }
        }
        return "";
    }

    private long longValue(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) && entry.getValue() instanceof Number number) {
                return number.longValue();
            }
        }
        return 0L;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeEnum(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }

    private Double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }
}
