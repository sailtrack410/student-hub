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
import com.studenthub.modules.schedule.entity.ScheduleClassCourseEntity;
import com.studenthub.modules.schedule.entity.ScheduleCourseEntity;
import com.studenthub.modules.schedule.mapper.ScheduleClassCourseMapper;
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
    private final ScheduleClassCourseMapper classCourseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleImportResponse importSchedule(AuthenticatedUser user) {
        JwBindingEntity binding = requireAuthenticatedBinding(user.userId());
        Optional<JwSessionStore.JwSession> session = jwSessionStore.find(user.userId());
        if (session.isEmpty()) {
            throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
        }

        JwSchedulePage page = scheduleClient.fetchSchedulePage(session.get().cookieHeader(), null);

        // 1. 重写个人课表（当前学期）
        courseMapper.deleteByUserIdAndSemester(user.userId(), page.semesterCode());
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

        // 2. 班级缓存：同一班级共用一份课表（学生班级相同，课基本一致；排除选修见同步任务）
        int classCacheCount = 0;
        String className = binding.getClassName();
        if (className != null && !className.isBlank()) {
            classCourseMapper.deleteByClassAndSemester(className, page.semesterCode());
            List<ScheduleClassCourseEntity> classCourses = new ArrayList<>();
            for (JwCourseEntry entry : page.courses()) {
                ScheduleClassCourseEntity entity = new ScheduleClassCourseEntity();
                entity.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
                entity.setCreatedBy(user.userId());
                entity.setUpdatedBy(user.userId());
                entity.setClassCode(className);
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
                classCourses.add(entity);
            }
            if (!classCourses.isEmpty()) {
                classCourseMapper.insertBatch(classCourses);
            }
            classCacheCount = classCourses.size();
        }

        log.info("课表导入完成, userId={}, semester={}, personal={}, classCache={}",
                user.userId(), page.semesterCode(), page.courses().size(), classCacheCount);
        return new ScheduleImportResponse(page.semesterCode(), page.semesterName(),
                className, page.courses().size(), classCacheCount);
    }

    @Override
    public ScheduleViewResponse view(AuthenticatedUser user) {
        List<ScheduleCourseEntity> courses = courseMapper.selectByUserId(user.userId());
        JwBindingEntity binding = jwBindingMapper.findByUserId(user.userId());
        String semesterCode = courses.isEmpty() ? null : courses.get(0).getSemesterCode();
        String semesterName = courses.isEmpty() ? null : courses.get(0).getSemesterName();
        String className = binding == null ? null : binding.getClassName();
        List<ScheduleCourseItem> items = courses.stream().map(ScheduleCourseItem::from).toList();
        return new ScheduleViewResponse(semesterCode, semesterName, className, items);
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