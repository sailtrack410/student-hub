package com.studenthub.modules.aichat.intent.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.aichat.controller.dto.AiActionResponse;
import com.studenthub.modules.aichat.intent.AiAssistantDataService;
import com.studenthub.modules.aichat.intent.AiAssistantResolved;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.schedule.calendar.SemesterCalendar;
import com.studenthub.modules.schedule.entity.ScheduleCourseEntity;
import com.studenthub.modules.schedule.entity.ScheduleSemesterEntity;
import com.studenthub.modules.schedule.mapper.ScheduleCourseMapper;
import com.studenthub.modules.schedule.mapper.ScheduleSemesterMapper;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
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

    private final JwBindingMapper jwBindingMapper;
    private final ScheduleCourseMapper courseMapper;
    private final ScheduleSemesterMapper semesterMapper;
    private final ServiceRequestMapper requestMapper;
    private final ServiceItemMapper itemMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AiAssistantResolved resolveSchedule(Long userId, LocalDate date, Integer dayOfWeek) {
        JwBindingEntity binding = jwBindingMapper.findByUserId(userId);
        List<ScheduleCourseEntity> courses = courseMapper.selectByUserId(userId);
        List<ScheduleSemesterEntity> semesters = semesterMapper.selectByUserId(userId);
        LocalDate today = LocalDate.now();

        // 确定要查的学期。有具体日期时按“学期锚点覆盖范围”精确匹配（绕开教务 code 学期位歧义）；
        // 无具体日期时用最后一次同步学期，其次取登记表里最新的学期。
        ScheduleSemesterEntity chosen = null;
        if (date != null) {
            chosen = pickSemesterForDate(semesters, date);
        } else {
            // 无具体日期（“本周/周X”）：按今天落点选学期，其次最后一次同步，再次取编码最新
            chosen = pickSemesterForDate(semesters, today);
            if (chosen == null) {
                String preferred = binding != null ? binding.getLastSemesterCode() : null;
                if (preferred != null) {
                    chosen = semesters.stream()
                            .filter(s -> preferred.equals(s.getSemesterCode()))
                            .findFirst()
                            .orElse(null);
                }
            }
            if (chosen == null) {
                chosen = semesters.stream()
                        .filter(s -> s.getSemesterCode() != null)
                        .max(java.util.Comparator.comparing(ScheduleSemesterEntity::getSemesterCode))
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
        if (chosen == null && binding != null && binding.getLastSemesterCode() != null) {
            final String fallbackCode = binding.getLastSemesterCode();
            List<ScheduleCourseEntity> fallbackCourses = courses.stream()
                    .filter(c -> fallbackCode.equals(c.getSemesterCode()))
                    .toList();
            if (fallbackCourses.isEmpty()) {
                return AiAssistantResolved.of("该用户尚未同步过课表，无法提供课表信息。",
                        List.of(new AiActionResponse("SCHEDULE", "去查看/同步课表", "SCHEDULE-VIEW", null,
                                "/pages/schedule/index")));
            }
            chosen = new ScheduleSemesterEntity();
            chosen.setSemesterCode(fallbackCode);
            chosen.setSemesterName(fallbackCourses.get(0).getSemesterName());
            chosen.setFirstMonday(SemesterCalendar.firstMonday(fallbackCode, fallbackCourses.get(0).getSemesterName()));
            chosen.setTotalWeeks(Math.min(Math.max(SemesterCalendar.maxWeekOf(fallbackCourses), 1), 30));
        }
        if (chosen == null) {
            return AiAssistantResolved.of("该用户尚未同步过课表，无法提供课表信息。",
                    List.of(new AiActionResponse("SCHEDULE", "去查看/同步课表", "SCHEDULE-VIEW", null,
                            "/pages/schedule/index")));
        }

        final String code = chosen.getSemesterCode();
        LocalDate firstMonday = chosen.getFirstMonday() != null
                ? chosen.getFirstMonday()
                : SemesterCalendar.firstMonday(code, chosen.getSemesterName());

        List<ScheduleCourseEntity> current = courses.stream()
                .filter(course -> code != null && code.equals(course.getSemesterCode()))
                .sorted(Comparator.comparing(ScheduleCourseEntity::getDayOfWeek)
                        .thenComparing(ScheduleCourseEntity::getStartSection))
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

        List<ScheduleCourseEntity> matched = current.stream()
                .filter(course -> {
                    // 周次未定位（学期锚点缺失且日期无法换算）时退回只看周几
                    if (!weekScoped) {
                        return course.getDayOfWeek() != null && targetDays.contains(course.getDayOfWeek());
                    }
                    return course.getDayOfWeek() != null
                            && targetDays.contains(course.getDayOfWeek())
                            && SemesterCalendar.activeAtWeek(course.getWeeks(), course.getWeekParity(), week);
                })
                .toList();

        int totalWeeks = chosen.getTotalWeeks() == null ? 30 : Math.max(chosen.getTotalWeeks(), 1);
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
        for (ScheduleCourseEntity course : matched) {
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

    private static ScheduleSemesterEntity pickSemesterForDate(List<ScheduleSemesterEntity> semesters, LocalDate date) {
        return semesters.stream()
                .filter(s -> s.getFirstMonday() != null)
                .filter(s -> {
                    int total = s.getTotalWeeks() == null ? 16 : Math.max(s.getTotalWeeks(), 1);
                    return !date.isBefore(s.getFirstMonday())
                            && date.isBefore(s.getFirstMonday().plusDays(total * 7L));
                })
                .max(java.util.Comparator.comparing(ScheduleSemesterEntity::getFirstMonday))
                .orElse(null);
    }

    private static String semesterLabel(List<ScheduleSemesterEntity> semesters,
                                        List<ScheduleCourseEntity> courses, String code) {
        return semesters.stream()
                .filter(s -> code.equals(s.getSemesterCode()))
                .map(ScheduleSemesterEntity::getSemesterName)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseGet(() -> courses.stream()
                        .filter(c -> code.equals(c.getSemesterCode()))
                        .map(ScheduleCourseEntity::getSemesterName)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(code));
    }

    @Override
    public AiAssistantResolved resolveMyRequests(Long userId, String statusFilter) {
        LambdaQueryWrapper<ServiceRequestEntity> query = new LambdaQueryWrapper<ServiceRequestEntity>()
                .eq(ServiceRequestEntity::getRequesterId, userId)
                .orderByDesc(ServiceRequestEntity::getSubmittedAt)
                .last("LIMIT " + REQUEST_LIMIT);
        if ("IN_PROGRESS".equals(statusFilter)) {
            query.in(ServiceRequestEntity::getStatus, List.of("SUBMITTED", "PROCESSING"));
        } else if ("COMPLETED".equals(statusFilter)) {
            query.eq(ServiceRequestEntity::getStatus, "COMPLETED");
        }

        List<ServiceRequestEntity> requests = requestMapper.selectList(query);
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
        for (ServiceRequestEntity request : requests) {
            context.append("- ").append(index + 1).append(". ")
                    .append(nullTo(request.getItemName()))
                    .append("｜").append(nullTo(request.getTitle()))
                    .append("｜状态:").append(statusText(request.getStatus()))
                    .append("｜编号:").append(nullTo(request.getRequestNo()));
            if (request.getSubmittedAt() != null) {
                context.append("｜提交于").append(DT.format(request.getSubmittedAt()));
            }
            context.append("\n");
            if (index < 3) {
                actions.add(new AiActionResponse("REQUEST_DETAIL",
                        "查看「" + nullTo(request.getItemName()) + "」详情", null, request.getId(),
                        "/pages/request/detail?id=" + request.getId()));
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
        ServiceItemEntity item = itemMapper.selectOne(new LambdaQueryWrapper<ServiceItemEntity>()
                .eq(ServiceItemEntity::getItemCode, itemCode)
                .eq(ServiceItemEntity::getCampusId, campusId)
                .eq(ServiceItemEntity::getStatus, "ENABLED")
                .last("LIMIT 1"));
        if (item == null) {
            return AiAssistantResolved.of("该服务暂不可用。", List.of());
        }
        return AiAssistantResolved.of("用户想办理的服务：" + nullTo(item.getItemName())
                        + "（服务项编码 " + itemCode + "，id=" + item.getId() + "）。",
                List.of(new AiActionResponse("SERVICE_APPLY", "去" + nullTo(item.getItemName()),
                        itemCode, item.getId(), "/pages/service/apply?itemId=" + item.getId())));
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

    private static String describeCourse(ScheduleCourseEntity course) {
        return "- " + course.getCourseName()
                + "｜老师:" + nullTo(course.getTeacher())
                + "｜地点:" + nullTo(course.getLocation())
                + "｜第" + course.getStartSection() + "-" + course.getEndSection() + "节"
                + "｜周次:" + nullTo(course.getWeeks())
                + parityText(course.getWeekParity())
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