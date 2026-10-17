package com.studenthub.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.integration.jw.KingosoftAdjustmentClient;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.jwauth.security.JwSessionStore;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.schedule.entity.ScheduleAdjustmentEntity;
import com.studenthub.modules.schedule.mapper.ScheduleAdjustmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 调课通知采集与定向通知：每次课表同步周期附带触发一次（全校一条上游请求），
 * 按 semesterCode+noticeTime+courseCode+classNo 幂等去重；新公告按
 * targetClasses ∩ jw_binding.className 定向通知受影响同学（班名对不上时退化为全班广播并告警）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleAdjustmentSyncService {

    private final KingosoftAdjustmentClient adjustmentClient;
    private final ScheduleAdjustmentMapper adjustmentMapper;
    private final JwBindingMapper jwBindingMapper;
    private final JwSessionStore jwSessionStore;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /** 采集一次并落库，返回本次新增条数；无可用会话或上游异常时返回 0（不影响班级采样同步） */
    public int syncOnce() {
        List<JwBindingEntity> bindings = jwBindingMapper.selectAllAuthenticatedStudents();
        if (bindings.isEmpty()) {
            return 0;
        }
        for (JwBindingEntity binding : bindings) {
            var session = jwSessionStore.find(binding.getUserId());
            if (session.isEmpty()) {
                continue;
            }
            try {
                List<ScheduleAdjustmentEntity> fetched =
                        adjustmentClient.fetchAdjustments(session.get().cookieHeader(), currentSemesterCode(binding));
                return persistAndNotify(fetched, bindings);
            } catch (Exception exception) {
                // 单个采样源失败换下一个（会话失效等），全部失败则本轮放弃
                log.info("调课通知采样失败, userId={}, msg={}", binding.getUserId(), exception.getMessage());
            }
        }
        log.info("调课通知采集：无可用会话，本轮跳过");
        return 0;
    }

    private String currentSemesterCode(JwBindingEntity binding) {
        return binding.getLastSemesterCode() == null ? "" : binding.getLastSemesterCode();
    }

    private int persistAndNotify(List<ScheduleAdjustmentEntity> fetched, List<JwBindingEntity> bindings) {
        int inserted = 0;
        for (ScheduleAdjustmentEntity entity : fetched) {
            if (isDuplicate(entity)) {
                continue;
            }
            try {
                adjustmentMapper.insert(entity);
                inserted++;
                notifyAffected(entity, bindings);
            } catch (Exception exception) {
                log.warn("调课公告入库失败, course={}, noticeTime={}, msg={}",
                        entity.getCourseName(), entity.getNoticeTime(), exception.getMessage());
            }
        }
        log.info("调课通知入库完成, 新增={}/{}", inserted, fetched.size());
        return inserted;
    }

    private boolean isDuplicate(ScheduleAdjustmentEntity entity) {
        return adjustmentMapper.selectCount(new LambdaQueryWrapper<ScheduleAdjustmentEntity>()
                .eq(ScheduleAdjustmentEntity::getSemesterCode, entity.getSemesterCode())
                .eq(ScheduleAdjustmentEntity::getNoticeTime, entity.getNoticeTime())
                .eq(ScheduleAdjustmentEntity::getCourseCode, entity.getCourseCode())
                .eq(ScheduleAdjustmentEntity::getClassNo, entity.getClassNo())) > 0;
    }

    /** 定向通知：targetClasses 与绑定班级取交集；交集为空但公告本身有班级标注时按班名对不上告警 */
    private void notifyAffected(ScheduleAdjustmentEntity entity, List<JwBindingEntity> bindings) {
        Set<String> targetClasses = parseTargetClasses(entity);
        if (targetClasses.isEmpty()) {
            // 公告未标注班级：无法定向，退化为全员（保持旧行为）——部分公告本身没有班级字段
            broadcast(entity, bindings);
            return;
        }
        List<JwBindingEntity> affected = bindings.stream()
                .filter(binding -> targetClasses.contains(binding.getClassName()))
                .toList();
        if (affected.isEmpty()) {
            log.warn("调课通知班名全部对不上, course={}, targetClasses={}, 已知班级={}",
                    entity.getCourseName(), targetClasses,
                    bindings.stream().map(JwBindingEntity::getClassName).distinct().limit(10).toList());
            return;
        }
        for (JwBindingEntity binding : affected) {
            sendQuietly(binding.getUserId(), entity);
        }
        log.info("调课定向通知完成, course={}, 目标班级={}, 通知 {} 人",
                entity.getCourseName(), targetClasses, affected.size());
    }

    private Set<String> parseTargetClasses(ScheduleAdjustmentEntity entity) {
        try {
            return new HashSet<>(objectMapper.readValue(
                    entity.getTargetClasses() == null ? "[]" : entity.getTargetClasses(),
                    new TypeReference<List<String>>() {
                    }));
        } catch (Exception exception) {
            return Set.of();
        }
    }

    private void broadcast(ScheduleAdjustmentEntity entity, List<JwBindingEntity> bindings) {
        for (JwBindingEntity binding : bindings) {
            sendQuietly(binding.getUserId(), entity);
        }
    }

    private void sendQuietly(Long userId, ScheduleAdjustmentEntity entity) {
        try {
            notificationService.send(new NotificationSendCommand(
                    userId, "SYSTEM", "调课通知", entity.getSummary(),
                    "SCHEDULE_ADJUSTMENT", entity.getId()));
        } catch (Exception exception) {
            log.warn("调课通知发送失败, userId={}", userId);
        }
    }
}
