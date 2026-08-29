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
import com.studenthub.modules.schedule.calendar.SemesterCalendar;
import com.studenthub.modules.schedule.controller.dto.ScheduleCourseItem;
import com.studenthub.modules.schedule.controller.dto.ScheduleImportResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleViewResponse;
import com.studenthub.modules.schedule.entity.ScheduleCourseEntity;
import com.studenthub.modules.schedule.entity.ScheduleSemesterEntity;
import com.studenthub.modules.schedule.mapper.ScheduleCourseMapper;
import com.studenthub.modules.schedule.mapper.ScheduleSemesterMapper;
import com.studenthub.modules.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final ScheduleSemesterMapper semesterMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ScheduleImportResponse importSchedule(AuthenticatedUser user, String semesterCode) {
        JwBindingEntity binding = requireAuthenticatedBinding(user.userId());
        Optional<JwSessionStore.JwSession> session = jwSessionStore.find(user.userId());
        if (session.isEmpty()) {
            markExpired(binding);
            throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
        }

        JwSchedulePage page;
        try {
            page = scheduleClient.fetchSchedulePage(session.get().cookieHeader(), semesterCode);
        } catch (BusinessException exception) {
            // 远端会话已失效时同步翻转认证状态，保持个人中心与同步提示一致。
            // 注意此处必须发生在事务之外，否则随后抛出的异常会把标记一起回滚。
            if (ErrorCode.JW_SESSION_EXPIRED.getCode().equals(exception.getCode())) {
                markExpired(binding);
            }
            throw exception;
        }

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
        final String syncSemesterCode = page.semesterCode();
        final String syncSemesterName = page.semesterName();
        final LocalDate firstMonday = SemesterCalendar.firstMonday(syncSemesterCode, syncSemesterName);
        final int totalWeeks = personal.isEmpty() ? 16
                : Math.min(Math.max(SemesterCalendar.maxWeekOf(personal), 1), 30);
        transactionTemplate.executeWithoutResult(status -> {
            // 个人课表按学期覆盖：同一学期重新导入时替换旧数据，其它学期保留
            courseMapper.deleteByUserIdAndSemester(user.userId(), syncSemesterCode);
            if (!personal.isEmpty()) {
                courseMapper.insertBatch(personal);
            }
            // 记录/更新该学期（空学期也登记，保证下拉里出现），并固化日期定位锚点
            ScheduleSemesterEntity existing = semesterMapper.selectByUserId(user.userId()).stream()
                    .filter(s -> syncSemesterCode.equals(s.getSemesterCode()))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                ScheduleSemesterEntity created = new ScheduleSemesterEntity();
                created.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
                created.setUserId(user.userId());
                created.setCreatedBy(user.userId());
                created.setUpdatedBy(user.userId());
                created.setSemesterCode(syncSemesterCode);
                created.setSemesterName(syncSemesterName);
                created.setLastSyncedAt(LocalDateTime.now());
                created.setFirstMonday(firstMonday);
                created.setTotalWeeks(totalWeeks);
                semesterMapper.insert(created);
            } else {
                existing.setSemesterName(syncSemesterName);
                existing.setLastSyncedAt(LocalDateTime.now());
                existing.setFirstMonday(firstMonday);
                existing.setTotalWeeks(totalWeeks);
                existing.setUpdatedBy(user.userId());
                semesterMapper.updateById(existing);
            }
            // 最新同步学期写回绑定，供视图默认选中
            JwBindingEntity semesterUpdate = new JwBindingEntity();
            semesterUpdate.setId(binding.getId());
            semesterUpdate.setLastSemesterCode(syncSemesterCode);
            semesterUpdate.setLastSemesterName(syncSemesterName);
            semesterUpdate.setVersion(binding.getVersion());
            jwBindingMapper.updateById(semesterUpdate);
        });

        String className = binding.getClassName();
        log.info("课表导入完成, userId={}, semester={}, personal={}",
                user.userId(), page.semesterCode(), page.courses().size());
        return new ScheduleImportResponse(page.semesterCode(), page.semesterName(),
                className, page.courses().size(), 0);
    }

    @Override
    public ScheduleViewResponse view(AuthenticatedUser user, String semesterCode) {
        List<ScheduleCourseEntity> courses = courseMapper.selectByUserId(user.userId());
        JwBindingEntity binding = jwBindingMapper.findByUserId(user.userId());
        List<ScheduleSemesterEntity> registered = semesterMapper.selectByUserId(user.userId());

        // 学期以最后一次同步为准（写在绑定上，新学期 0 门课时依然有值）；
        // 未同步过则回退到登记表/课程行里最新的学期
        String defaultCode;
        if (binding != null && binding.getLastSemesterCode() != null) {
            defaultCode = binding.getLastSemesterCode();
        } else {
            defaultCode = registered.stream()
                    .map(ScheduleSemesterEntity::getSemesterCode)
                    .filter(java.util.Objects::nonNull)
                    .max(String::compareTo)
                    .orElseGet(() -> courses.stream()
                            .map(ScheduleCourseEntity::getSemesterCode)
                            .filter(java.util.Objects::nonNull)
                            .max(String::compareTo)
                            .orElse(null));
        }

        String requested = semesterCode == null || semesterCode.isBlank() ? defaultCode : semesterCode.trim();
        List<ScheduleCourseEntity> current = requested == null ? List.of()
                : courses.stream()
                        .filter(course -> requested.equals(course.getSemesterCode()))
                        .toList();

        // 可选学期：全部导入记录（累积，含空学期），再补上课程行里出现但漏登记的
        java.util.LinkedHashMap<String, ScheduleViewResponse.SemesterOption> byCode = new java.util.LinkedHashMap<>();
        for (ScheduleSemesterEntity s : registered) {
            if (s.getSemesterCode() != null) {
                byCode.put(s.getSemesterCode(),
                        new ScheduleViewResponse.SemesterOption(s.getSemesterCode(), s.getSemesterName()));
            }
        }
        for (ScheduleCourseEntity course : courses) {
            if (course.getSemesterCode() != null && !byCode.containsKey(course.getSemesterCode())) {
                byCode.put(course.getSemesterCode(),
                        new ScheduleViewResponse.SemesterOption(course.getSemesterCode(), course.getSemesterName()));
            }
        }
        List<ScheduleViewResponse.SemesterOption> available = new java.util.ArrayList<>(byCode.values());
        available.sort(java.util.Comparator
                .comparing(ScheduleViewResponse.SemesterOption::semesterCode,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));

        List<ScheduleCourseItem> items = current.stream().map(ScheduleCourseItem::from).toList();
        String className = binding == null ? null : binding.getClassName();

        // 日期定位锚点：优先登记表，缺失时按同规则现场计算（兼容旧数据）
        ScheduleSemesterEntity chosen = requested == null ? null
                : registered.stream()
                        .filter(s -> requested.equals(s.getSemesterCode()))
                        .findFirst()
                        .orElse(null);
        LocalDate firstMonday = chosen != null ? chosen.getFirstMonday() : null;
        if (firstMonday == null) {
            String nameForCalc = requested == null ? null : (byCode.containsKey(requested)
                    ? byCode.get(requested).semesterName()
                    : (binding != null && requested.equals(binding.getLastSemesterCode())
                            ? binding.getLastSemesterName() : null));
            firstMonday = SemesterCalendar.firstMonday(requested, nameForCalc);
        }
        Integer totalWeeks = chosen != null ? chosen.getTotalWeeks() : null;
        if (totalWeeks == null) {
            totalWeeks = current.isEmpty() ? 16
                    : Math.min(Math.max(SemesterCalendar.maxWeekOf(current), 1), 30);
        }

        String chosenName = requested == null ? null
                : byCode.containsKey(requested)
                        ? byCode.get(requested).semesterName()
                        : (binding != null && requested.equals(binding.getLastSemesterCode())
                                ? binding.getLastSemesterName() : null);
        return new ScheduleViewResponse(requested, chosenName, className, items, available, firstMonday, totalWeeks);
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