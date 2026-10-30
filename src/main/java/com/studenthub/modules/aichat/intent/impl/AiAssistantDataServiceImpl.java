package com.studenthub.modules.aichat.intent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.aichat.controller.dto.AiActionResponse;
import com.studenthub.modules.aichat.intent.AiAssistantDataService;
import com.studenthub.modules.aichat.intent.AiAssistantResolved;
import com.studenthub.modules.schedule.calendar.SemesterCalendar;
import com.studenthub.modules.jwauth.query.JwBindingQuery;
import com.studenthub.modules.schedule.query.ScheduleQuery;
import com.studenthub.modules.servicecatalog.query.ServiceCatalogQuery;
import com.studenthub.modules.servicerequest.query.ServiceRequestQuery;
import com.studenthub.modules.workorder.statemachine.WorkOrderTransitions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantDataServiceImpl implements AiAssistantDataService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final int REQUEST_LIMIT = 8;
    /** 注入提示词的课程描述条数上限，避免一周课程过大导致生成超时 */
    private static final int SCHEDULE_CONTEXT_LIMIT = 40;

    private static final Map<String, String> REQUEST_STATUS_TEXT;

    static {
        // 工单状态文案以 WorkOrderTransitions.Status.display 为唯一来源，新增状态只改枚举
        Map<String, String> texts = new LinkedHashMap<>();
        for (WorkOrderTransitions.Status status : WorkOrderTransitions.Status.values()) {
            texts.put(status.name(), status.getDisplay());
        }
        REQUEST_STATUS_TEXT = Map.copyOf(texts);
    }

    private final JwBindingQuery jwBindingQuery;
    private final ScheduleQuery scheduleQuery;
    private final ServiceRequestQuery requestQuery;
    private final ServiceCatalogQuery catalogQuery;
    private final ObjectMapper objectMapper;

    @Override
    public AiAssistantResolved resolveSchedule(Long userId, LocalDate date, Integer dayOfWeek) {
        String lastSemesterCode = jwBindingQuery.lastSemesterCode(userId);
        ScheduleQuery.Snapshot snapshot = scheduleQuery.forUser(userId);
        List<ScheduleQuery.Course> courses = snapshot.courses();
        List<ScheduleQuery.Semester> semesters = snapshot.semesters();
        LocalDate today = LocalDate.now();

        // 确定要查的学期。有具体日期时按“学期锚点覆盖范围”精确匹配（绕开教务 code 学期位歧义）；
        // 无具体日期时用最后一次同步学期，其次取登记表里最新的学期。
        ScheduleQuery.Semester chosen = null;
        if (date != null) {
            chosen = pickSemesterForDate(semesters, date);
        } else {
            // 无具体日期（“本周/周X”）：按今天落点选学期，其次最后一次同步，再次取编码最新
            chosen = pickSemesterForDate(semesters, today);
            if (chosen == null) {
                String preferred = lastSemesterCode;
                if (preferred != null) {
                    chosen = semesters.stream()
                            .filter(s -> preferred.equals(s.semesterCode()))
                            .findFirst()
                            .orElse(null);
                }
            }
            if (chosen == null) {
                chosen = semesters.stream()
                        .filter(s -> s.semesterCode() != null)
                        .max(java.util.Comparator.comparing(ScheduleQuery.Semester::semesterCode))
                        .orElse(null);
            }
        }

        // 有日期但落不到任何已导入学期：提示去同步该学期
        if (date != null && chosen == null) {
            return AiAssistantResolved.of(date.getMonthValue() + "月" + date.getDayOfMonth()
                            + "日不在已导入的学期范围内，请先到课表页点“同步”拉取对应学期课程。",
                    List.of(new AiActionResponse("SCHEDULE", "去同步课表", "SCHEDULE-VIEW", null,
                            "/pages/schedule/index")));
        }

        if (chosen == null && courses.isEmpty() && semesters.isEmpty()) {
            return AiAssistantResolved.of("该用户尚未同步过课表，无法提供课表信息。",
                    List.of(new AiActionResponse("SCHEDULE", "去查看/同步课表", "SCHEDULE-VIEW", null,
                            "/pages/schedule/index")));
        }

        // 已登记学期为空、但库里有课程（极端旧数据）：退回最后一次同步学期并按课程反查
        if (chosen == null && lastSemesterCode != null) {
            final String fallbackCode = lastSemesterCode;
            List<ScheduleQuery.Course> fallbackCourses = courses.stream()
                    .filter(c -> fallbackCode.equals(c.semesterCode()))
                    .toList();
            if (fallbackCourses.isEmpty()) {
                return AiAssistantResolved.of("该用户尚未同步过课表，无法提供课表信息。",
                        List.of(new AiActionResponse("SCHEDULE", "去查看/同步课表", "SCHEDULE-VIEW", null,
                                "/pages/schedule/index")));
            }
            String semesterName = fallbackCourses.get(0).semesterName();
            int totalWeeks = fallbackCourses.stream()
                    .mapToInt(c -> SemesterCalendar.maxWeekIn(c.weeks())).max().orElse(1);
            chosen = new ScheduleQuery.Semester(fallbackCode, semesterName,
                    SemesterCalendar.firstMonday(fallbackCode, semesterName),
                    Math.min(Math.max(totalWeeks, 1), 30));
        }
        if (chosen == null) {
            return AiAssistantResolved.of("该用户尚未同步过课表，无法提供课表信息。",
                    List.of(new AiActionResponse("SCHEDULE", "去查看/同步课表", "SCHEDULE-VIEW", null,
                            "/pages/schedule/index")));
        }

        final String code = chosen.semesterCode();
        LocalDate firstMonday = chosen.firstMonday() != null
                ? chosen.firstMonday()
                : SemesterCalendar.firstMonday(code, chosen.semesterName());

        List<ScheduleQuery.Course> current = courses.stream()
                .filter(course -> code != null && code.equals(course.semesterCode()))
                .sorted(Comparator.comparing(ScheduleQuery.Course::dayOfWeek)
                        .thenComparing(ScheduleQuery.Course::startSection))
                .toList();

        // 把问法转换成“周次 + 各天的集合”
        int week;
        boolean weekScoped;
        java.util.Set<Integer> targetDays = new java.util.LinkedHashSet<>();
        String scopeText;
        if (date != null) {
            week = SemesterCalendar.weekOf(date, firstMonday);
            weekScoped = true;
            targetDays.add(date.getDayOfWeek().getValue());
            scopeText = date.getMonthValue() + "月" + date.getDayOfMonth() + "日（"
                    + "周" + "一二三四五六日".charAt(date.getDayOfWeek().getValue() - 1) + "·第" + week + "周）";
        } else {
            int todayWeek = SemesterCalendar.weekOf(today, firstMonday);
            week = Math.max(todayWeek, 1);
            weekScoped = todayWeek >= 1;
            if (dayOfWeek != null) {
                targetDays.add(dayOfWeek);
                scopeText = "本周周" + "一二三四五六日".charAt(dayOfWeek - 1) + "（第" + week + "周）";
            } else {
                for (int d = 1; d <= 7; d++) {
                    targetDays.add(d);
                }
                scopeText = "本周（第" + week + "周）";
            }
        }

        List<ScheduleQuery.Course> matched = current.stream()
                .filter(course -> {
                    // 周次未定位（学期锚点缺失且日期无法换算）时退回只看周几
                    if (!weekScoped) {
                        return course.dayOfWeek() != null && targetDays.contains(course.dayOfWeek());
                    }
                    return course.dayOfWeek() != null
                            && targetDays.contains(course.dayOfWeek())
                            && SemesterCalendar.activeAtWeek(course.weeks(), course.weekParity(), week);
                })
                .toList();

        int totalWeeks = chosen.totalWeeks() == null ? 30 : Math.max(chosen.totalWeeks(), 1);
        if (date != null && (week < 1 || week > totalWeeks)) {
            return AiAssistantResolved.of(scopeText + "：该日期不在「"
                            + semesterLabel(semesters, courses, code) + "」的教学周内。",
                    List.of(new AiActionResponse("SCHEDULE", "去查看课表", "SCHEDULE-VIEW", null,
                            "/pages/schedule/index")));
        }

        if (matched.isEmpty()) {
            String text = current.isEmpty()
                    ? "「" + semesterLabel(semesters, courses, code) + "」暂未导入课程数据（可能是假期或尚未排课）。"
                    : scopeText + "没有课。";
            return AiAssistantResolved.of(text,
                    List.of(new AiActionResponse("SCHEDULE", "去查看课表", "SCHEDULE-VIEW", null,
                            "/pages/schedule/index")));
        }

        StringBuilder context = new StringBuilder();
        context.append("用户课表（").append(scopeText).append("）。\n");
        int shown = 0;
        for (ScheduleQuery.Course course : matched) {
            if (shown >= SCHEDULE_CONTEXT_LIMIT) {
                context.append("……（其余课程略，共 ").append(matched.size()).append(" 条）\n");
                break;
            }
            context.append(describeCourse(course));
            shown++;
        }
        return AiAssistantResolved.of(context.toString(),
                List.of(new AiActionResponse("SCHEDULE", "去查看课表", "SCHEDULE-VIEW", null,
                        "/pages/schedule/index")));
    }

    private static ScheduleQuery.Semester pickSemesterForDate(List<ScheduleQuery.Semester> semesters, LocalDate date) {
        return semesters.stream()
                .filter(s -> s.firstMonday() != null)
                .filter(s -> {
                    int total = s.totalWeeks() == null ? 16 : Math.max(s.totalWeeks(), 1);
                    return !date.isBefore(s.firstMonday())
                            && date.isBefore(s.firstMonday().plusDays(total * 7L));
                })
                .max(java.util.Comparator.comparing(ScheduleQuery.Semester::firstMonday))
                .orElse(null);
    }

    private static String semesterLabel(List<ScheduleQuery.Semester> semesters,
                                        List<ScheduleQuery.Course> courses, String code) {
        return semesters.stream()
                .filter(s -> code.equals(s.semesterCode()))
                .map(ScheduleQuery.Semester::semesterName)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseGet(() -> courses.stream()
                        .filter(c -> code.equals(c.semesterCode()))
                        .map(ScheduleQuery.Course::semesterName)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(code));
    }

    @Override
    public AiAssistantResolved resolveMyRequests(Long userId, String statusFilter) {
        ServiceRequestQuery.StatusFilter filter = "IN_PROGRESS".equals(statusFilter)
                ? ServiceRequestQuery.StatusFilter.IN_PROGRESS
                : "COMPLETED".equals(statusFilter) ? ServiceRequestQuery.StatusFilter.COMPLETED
                : ServiceRequestQuery.StatusFilter.ALL;
        List<ServiceRequestQuery.Request> requests = requestQuery.recentForUser(userId, filter, REQUEST_LIMIT);
        if (requests.isEmpty()) {
            String hint = statusFilter == null ? "你目前还没有提交过申请。"
                    : statusFilter.equals("COMPLETED") ? "没有已完成的申请。" : "没有进行中的申请。";
            return AiAssistantResolved.of(hint,
                    List.of(new AiActionResponse("REQUEST_LIST", "去我的申请", null, null,
                            "/pages/request/list")));
        }

        StringBuilder context = new StringBuilder();
        context.append("用户最近的服务申请（").append(requests.size()).append("条）：\n");
        List<AiActionResponse> actions = new ArrayList<>();
        int index = 0;
        for (ServiceRequestQuery.Request request : requests) {
            context.append("- ").append(index + 1).append(". ")
                    .append(nullTo(request.itemName()))
                    .append("｜").append(nullTo(request.title()))
                    .append("｜状态:").append(statusText(request.status()))
                    .append("｜编号:").append(nullTo(request.requestNo()));
            if (request.submittedAt() != null) {
                context.append("｜提交于").append(DT.format(request.submittedAt()));
            }
            context.append("\n");
            if (index < 3) {
                actions.add(new AiActionResponse("REQUEST_DETAIL",
                        "查看「" + nullTo(request.itemName()) + "」详情", null, request.id(),
                        "/pages/request/detail?id=" + request.id()));
            }
            index++;
        }
        actions.add(new AiActionResponse("REQUEST_LIST", "查看全部申请", null, null, "/pages/request/list"));
        return AiAssistantResolved.of(context.toString(), actions);
    }

    @Override
    public AiAssistantResolved resolveServiceItem(String itemCode, Long campusId) {
        if ("SCHEDULE-VIEW".equals(itemCode)) {
            return AiAssistantResolved.of("用户想查看课表。",
                    List.of(new AiActionResponse("SCHEDULE", "去查看课表", itemCode, null, "/pages/schedule/index")));
        }
        ServiceCatalogQuery.Item item = catalogQuery.enabledItem(itemCode, campusId);
        if (item == null) {
            return AiAssistantResolved.of("该服务暂不可用。", List.of());
        }
        return AiAssistantResolved.of("用户想办理的服务：" + nullTo(item.itemName())
                        + "（服务项编码 " + itemCode + "，id=" + item.id() + "）。",
                List.of(new AiActionResponse("SERVICE_APPLY", "去" + nullTo(item.itemName()),
                        itemCode, item.id(), "/pages/service/apply?itemId=" + item.id())));
    }

    @Override
    public String serializeActions(List<AiActionResponse> actions) {
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(actions);
        } catch (Exception exception) {
            log.warn("AI 动作卡片序列化失败", exception);
            return null;
        }
    }

    private static String statusText(String status) {
        return REQUEST_STATUS_TEXT.getOrDefault(status, status == null ? "-" : status);
    }

    private static String describeCourse(ScheduleQuery.Course course) {
        return "- " + course.courseName()
                + "｜老师:" + nullTo(course.teacher())
                + "｜地点:" + nullTo(course.location())
                + "｜第" + course.startSection() + "-" + course.endSection() + "节"
                + "｜周次:" + nullTo(course.weeks())
                + parityText(course.weekParity())
                + "\n";
    }

    private static String parityText(Integer parity) {
        if (parity == null || parity == 0) {
            return "";
        }
        return parity == 2 ? "（双周）" : "（单周）";
    }

    private static String nullTo(String value) {
        return value == null || value.isBlank() ? "待定" : value;
    }
}
