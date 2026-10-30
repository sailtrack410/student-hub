package com.studenthub.modules.schedule.service.impl;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.integration.jw.KingosoftScheduleClient;
import com.studenthub.integration.jw.KingosoftScheduleClient.JwCourseEntry;
import com.studenthub.integration.jw.KingosoftScheduleClient.JwSchedulePage;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.jwauth.security.JwSessionStore;
import com.studenthub.modules.schedule.calendar.SemesterCalendar;
import com.studenthub.modules.schedule.calendar.SectionTimes;
import com.studenthub.modules.schedule.controller.dto.ScheduleCourseItem;
import com.studenthub.modules.schedule.controller.dto.ScheduleAdjustmentItem;
import com.studenthub.modules.schedule.controller.dto.ScheduleImportResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleNextCourseResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleViewResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleWeekViewResponse;
import com.studenthub.modules.schedule.entity.ScheduleAdjustmentEntity;
import com.studenthub.modules.schedule.entity.ScheduleClassCourseEntity;
import com.studenthub.modules.schedule.entity.ScheduleCourseEntity;
import com.studenthub.modules.schedule.entity.ScheduleSemesterEntity;
import com.studenthub.modules.schedule.mapper.ScheduleAdjustmentMapper;
import com.studenthub.modules.schedule.mapper.ScheduleClassCourseMapper;
import com.studenthub.modules.schedule.mapper.ScheduleCourseMapper;
import com.studenthub.modules.schedule.mapper.ScheduleSemesterMapper;
import com.studenthub.modules.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private final ScheduleClassCourseMapper classCourseMapper;
    private final ScheduleAdjustmentMapper adjustmentMapper;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

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
            entity.setTimeRange(entry.timeRange());
            entity.setRawText(entry.rawText());
            personal.add(entity);
        }
        final String syncSemesterCode = page.semesterCode();
        final String syncSemesterName = page.semesterName();
        final LocalDate firstMonday = SemesterCalendar.firstMonday(syncSemesterCode, syncSemesterName);
        final int totalWeeks = page.totalWeeks() != null ? page.totalWeeks()
                : personal.isEmpty() ? 25 : Math.max(SemesterCalendar.maxWeekOf(personal), 20);
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
            totalWeeks = current.isEmpty() ? 25
                    : Math.max(SemesterCalendar.maxWeekOf(current), 20);
        }

        String chosenName = requested == null ? null
                : byCode.containsKey(requested)
                        ? byCode.get(requested).semesterName()
                        : (binding != null && requested.equals(binding.getLastSemesterCode())
                                ? binding.getLastSemesterName() : null);
        return new ScheduleViewResponse(requested, chosenName, className, items, available, firstMonday, totalWeeks);
    }

    @Override
    public ScheduleWeekViewResponse weekView(AuthenticatedUser user, String semesterCode, Integer week) {
        WeekContext context = resolveWeekContext(user, semesterCode, week);
        // 按 dayOfWeek 分桶（周一~周日固定 7 桶），同一节次段内按起始节排序
        List<List<ScheduleCourseEntity>> buckets = bucketByDay(context.courses());
        List<ScheduleWeekViewResponse.DayView> days = new ArrayList<>(7);
        for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
            List<ScheduleWeekViewResponse.CourseView> courses = new ArrayList<>();
            for (ScheduleCourseEntity course : buckets.get(dayOfWeek - 1)) {
                if (!SemesterCalendar.activeAtWeek(course.getWeeks(), course.getWeekParity(), context.week())) {
                    continue;
                }
                String timeRange = course.getTimeRange() != null && !course.getTimeRange().isBlank()
                        ? course.getTimeRange()
                        : SectionTimes.rangeOf(course.getStartSection(), course.getEndSection());
                courses.add(new ScheduleWeekViewResponse.CourseView(
                        course.getCourseName(), course.getTeacher(), course.getLocation(),
                        course.getStartSection(), course.getEndSection(), timeRange));
            }
            days.add(new ScheduleWeekViewResponse.DayView(dayOfWeek,
                    context.weekStart().plusDays(dayOfWeek - 1L), courses));
        }
        return new ScheduleWeekViewResponse(context.semesterCode(), context.semesterName(),
                context.week(), context.weekStart(), context.currentWeek(), context.totalWeeks(), days);
    }

    @Override
    public ScheduleNextCourseResponse nextCourse(AuthenticatedUser user) {
        WeekContext context = resolveWeekContext(user, null, null);
        LocalDate today = LocalDate.now();
        int todayDayOfWeek = today.getDayOfWeek().getValue();
        LocalTime now = LocalTime.now();
        ScheduleNextCourseResponse.CourseView current = null;
        ScheduleNextCourseResponse.CourseView next = null;

        // 优先在"今天"找：当前节 → 之后的节；今天没有再找后续七天
        for (ScheduleCourseEntity course : context.courses()) {
            if (course.getDayOfWeek() != todayDayOfWeek
                    || !SemesterCalendar.activeAtWeek(course.getWeeks(), course.getWeekParity(), context.week())) {
                continue;
            }
            String timeRange = SectionTimes.rangeOf(course.getStartSection(), course.getEndSection());
            if (current == null && !sectionEnd(course.getEndSection()).isBefore(now)
                    && sectionStart(course.getStartSection()).isBefore(now)) {
                // 当前时间位于该课的节次段内（开始早于 now、结束不早于 now）
                current = toCourseView(course, timeRange);
            }
            if (next == null && sectionStart(course.getStartSection()).isAfter(now)) {
                next = toCourseView(course, timeRange);
            }
        }
        if (current == null && next == null) {
            // 今天没了：从明天起找下一个上课日
            for (int offset = 1; offset <= 7 && next == null; offset++) {
                LocalDate day = today.plusDays(offset);
                int dayOfWeek = day.getDayOfWeek().getValue();
                for (ScheduleCourseEntity course : context.courses()) {
                    if (course.getDayOfWeek() != dayOfWeek
                            || !SemesterCalendar.activeAtWeek(course.getWeeks(), course.getWeekParity(),
                                    context.week() + offset)) {
                        continue;
                    }
                    next = toCourseView(course, SectionTimes.rangeOf(course.getStartSection(), course.getEndSection()));
                    break;
                }
            }
        }
        return new ScheduleNextCourseResponse(current != null || next != null, current, next,
                context.currentWeek(), today);
    }

    @Override
    public List<ScheduleAdjustmentItem> adjustments(String semesterCode) {
        String code = semesterCode == null || semesterCode.isBlank()
                ? null : semesterCode.trim();
        if (code == null) {
            // 缺省取最新一条公告的学期
            ScheduleAdjustmentEntity latest = adjustmentMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScheduleAdjustmentEntity>()
                            .orderByDesc(ScheduleAdjustmentEntity::getNoticeTime)
                            .last("LIMIT 1")).stream().findFirst().orElse(null);
            if (latest == null) {
                return List.of();
            }
            code = latest.getSemesterCode();
        }
        return adjustmentMapper.selectBySemester(code).stream()
                .map(entity -> ScheduleAdjustmentItem.from(entity, parseTargetClasses(entity)))
                .toList();
    }

    /** targetClasses JSON 数组解析；坏数据返回空列表 */
    private List<String> parseTargetClasses(ScheduleAdjustmentEntity entity) {
        if (entity.getTargetClasses() == null || entity.getTargetClasses().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(entity.getTargetClasses(), new TypeReference<List<String>>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    /** 周展开共用上下文：学期、锚点、课程集（个人课表缺失时回落班级缓存，1.7） */
    private record WeekContext(String semesterCode, String semesterName, int week,
                               LocalDate weekStart, int currentWeek, int totalWeeks,
                               List<ScheduleCourseEntity> courses) {
    }

    private WeekContext resolveWeekContext(AuthenticatedUser user, String semesterCode, Integer week) {
        // 复用 view() 的学期与锚点推导：先取标准视图再取其课程集
        ScheduleViewResponse base = view(user, semesterCode);
        String code = base.semesterCode();
        JwBindingEntity binding = jwBindingMapper.findByUserId(user.userId());
        String className = binding != null ? binding.getClassName() : null;

        List<ScheduleCourseEntity> courses = courseMapper.selectByUserId(user.userId()).stream()
                .filter(course -> code != null && code.equals(course.getSemesterCode()))
                .toList();
        // 个人课表为空且班级已知：回落班级缓存（未手动同步的新用户也能看到班级课表）
        if (courses.isEmpty() && className != null && !className.isBlank()) {
            List<ScheduleClassCourseEntity> classCourses =
                    classCourseMapper.selectByClassAndSemester(className, code);
            if (!classCourses.isEmpty()) {
                log.info("个人课表缺失，回落班级缓存, userId={}, className={}", user.userId(), className);
                courses = classCourses.stream().map(this::fromClassCourse).toList();
            }
        }

        LocalDate firstMonday = base.firstMonday();
        int currentWeek = firstMonday == null ? 0 : Math.max(SemesterCalendar.weekOf(LocalDate.now(), firstMonday), 0);
        int requestedWeek = week != null && week > 0 ? week : currentWeek;
        if (requestedWeek <= 0) {
            requestedWeek = 1;
        }
        int maxWeek = base.totalWeeks() != null ? base.totalWeeks() : 25;
        if (requestedWeek > maxWeek) {
            throw new com.studenthub.exception.BusinessException(
                    com.studenthub.common.ErrorCode.INVALID_PARAMETER,
                    "周次超出本学期范围，请选择第1–" + maxWeek + "周");
        }
        LocalDate weekStart = firstMonday != null ? firstMonday.plusWeeks(requestedWeek - 1L) : LocalDate.now();
        return new WeekContext(code, base.semesterName(), requestedWeek, weekStart, currentWeek, maxWeek, courses);
    }

    /** 班级缓存行转个人课程行（字段同构，班级行缺 personal 维度则透传） */
    private ScheduleCourseEntity fromClassCourse(ScheduleClassCourseEntity entity) {
        ScheduleCourseEntity course = new ScheduleCourseEntity();
        course.setCourseName(entity.getCourseName());
        course.setTeacher(entity.getTeacher());
        course.setLocation(entity.getLocation());
        course.setDayOfWeek(entity.getDayOfWeek());
        course.setStartSection(entity.getStartSection());
        course.setEndSection(entity.getEndSection());
        course.setWeeks(entity.getWeeks());
        course.setWeekParity(entity.getWeekParity());
        course.setTimeRange(entity.getTimeRange());
        course.setSemesterCode(entity.getSemesterCode());
        course.setSemesterName(entity.getSemesterName());
        return course;
    }

    private List<List<ScheduleCourseEntity>> bucketByDay(List<ScheduleCourseEntity> courses) {
        List<List<ScheduleCourseEntity>> buckets = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            buckets.add(new ArrayList<>());
        }
        for (ScheduleCourseEntity course : courses) {
            int day = course.getDayOfWeek();
            if (day >= 1 && day <= 7) {
                buckets.get(day - 1).add(course);
            }
        }
        for (List<ScheduleCourseEntity> bucket : buckets) {
            bucket.sort(java.util.Comparator.comparingInt(ScheduleCourseEntity::getStartSection));
        }
        return buckets;
    }

    private ScheduleNextCourseResponse.CourseView toCourseView(ScheduleCourseEntity course, String timeRange) {
        return new ScheduleNextCourseResponse.CourseView(course.getCourseName(), course.getTeacher(),
                course.getLocation(), course.getDayOfWeek(), course.getStartSection(),
                course.getEndSection(), timeRange);
    }

    private LocalTime sectionStart(int section) {
        String range = SectionTimes.allRanges().get(section - 1);
        return LocalTime.parse(range.split("-")[0]);
    }

    private LocalTime sectionEnd(int section) {
        String range = SectionTimes.allRanges().get(section - 1);
        return LocalTime.parse(range.split("-")[1]);
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