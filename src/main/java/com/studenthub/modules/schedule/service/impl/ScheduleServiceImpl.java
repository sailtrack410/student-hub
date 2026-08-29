package com.studenthub.modules.schedule.service.impl;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.jw.KingosoftScheduleClient;
import com.studenthub.integration.jw.KingosoftScheduleClient.JwCourseEntry;
import com.studenthub.integration.jw.KingosoftScheduleClient.JwSchedulePage;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.jwauth.security.JwSessionStore;
import com.studenthub.modules.schedule.controller.dto.ScheduleCourseItem;
import com.studenthub.modules.schedule.controller.dto.ScheduleImportResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleViewResponse;
import com.studenthub.modules.schedule.entity.ScheduleCourseEntity;
import com.studenthub.modules.schedule.mapper.ScheduleCourseMapper;
import com.studenthub.modules.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final JwBindingMapper jwBindingMapper;
    private final JwSessionStore jwSessionStore;
    private final KingosoftScheduleClient scheduleClient;
    private final ScheduleCourseMapper courseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleImportResponse importSchedule(AuthenticatedUser user) {
        JwBindingEntity binding = requireAuthenticatedBinding(user.userId());
        Optional<JwSessionStore.JwSession> session = jwSessionStore.find(user.userId());
        if (session.isEmpty()) {
            markExpired(binding);
            throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
        }

        JwSchedulePage page;
        try {
            page = scheduleClient.fetchSchedulePage(session.get().cookieHeader(), null);
        } catch (BusinessException exception) {
            // 远端会话已失效时同步翻转认证状态，保持个人中心与同步提示一致
            if (ErrorCode.JW_SESSION_EXPIRED.getCode().equals(exception.getCode())) {
                markExpired(binding);
            }
            throw exception;
        }

        // 个人课表仅保留最新学期：整用户覆盖，避免学期切换后新旧数据混排
        courseMapper.deleteByUserId(user.userId());
        List<ScheduleCourseEntity> personal = new ArrayList<>();
        for (JwCourseEntry entry : page.courses()) {
            ScheduleCourseEntity entity = new ScheduleCourseEntity();
            entity.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            entity.setUserId(user.userId());
            entity.setCreatedBy(user.userId());
            entity.setUpdatedBy(user.userId());
            entity.setSemesterCode(page.semesterCode());
            entity.setSemesterName(page.semesterName());
            entity.setCourseName(entry.courseName());
            entity.setTeacher(entry.teacher());
            entity.setLocation(entry.location());
            entity.setDayOfWeek(entry.dayOfWeek());
            entity.setStartSection(entry.startSection());
            entity.setEndSection(entry.endSection());
            entity.setWeeks(entry.weeks());
            entity.setWeekParity(entry.weekParity());
            entity.setRawText(entry.rawText());
            personal.add(entity);
        }
        if (!personal.isEmpty()) {
            courseMapper.insertBatch(personal);
        }

        String className = binding.getClassName();
        log.info("课表导入完成, userId={}, semester={}, personal={}",
                user.userId(), page.semesterCode(), page.courses().size());
        return new ScheduleImportResponse(page.semesterCode(), page.semesterName(),
                className, page.courses().size(), 0);
    }

    @Override
    public ScheduleViewResponse view(AuthenticatedUser user) {
        List<ScheduleCourseEntity> courses = courseMapper.selectByUserId(user.userId());
        // 只展示最新学期，历史学期不入视图
        String latestSemester = courses.stream()
                .map(ScheduleCourseEntity::getSemesterCode)
                .filter(java.util.Objects::nonNull)
                .max(String::compareTo)
                .orElse(null);
        List<ScheduleCourseEntity> current = courses.stream()
                .filter(course -> latestSemester == null || latestSemester.equals(course.getSemesterCode()))
                .toList();
        JwBindingEntity binding = jwBindingMapper.findByUserId(user.userId());
        String semesterCode = current.isEmpty() ? null : current.get(0).getSemesterCode();
        String semesterName = current.isEmpty() ? null : current.get(0).getSemesterName();
        String className = binding == null ? null : binding.getClassName();
        List<ScheduleCourseItem> items = current.stream().map(ScheduleCourseItem::from).toList();
        return new ScheduleViewResponse(semesterCode, semesterName, className, items);
    }

    private void markExpired(JwBindingEntity binding) {
        JwBindingEntity update = new JwBindingEntity();
        update.setId(binding.getId());
        update.setAuthStatus("EXPIRED");
        update.setVersion(binding.getVersion());
        jwBindingMapper.updateById(update);
    }

    private JwBindingEntity requireAuthenticatedBinding(Long userId) {
        JwBindingEntity binding = jwBindingMapper.findByUserId(userId);
        if (binding == null || !"AUTHENTICATED".equals(binding.getAuthStatus())
                || !"STUDENT".equals(binding.getIdentityKind())) {
            // 课表导入仅学生可用
            throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
        }
        return binding;
    }
}