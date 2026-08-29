package com.studenthub.modules.aichat.intent.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.aichat.controller.dto.AiActionResponse;
import com.studenthub.modules.aichat.intent.AiAssistantDataService;
import com.studenthub.modules.aichat.intent.AiAssistantResolved;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.schedule.entity.ScheduleCourseEntity;
import com.studenthub.modules.schedule.mapper.ScheduleCourseMapper;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    private static final Map<String, String> REQUEST_STATUS_TEXT = Map.of(
            "SUBMITTED", "待受理",
            "PROCESSING", "处理中",
            "COMPLETED", "已完成",
            "REJECTED", "未受理",
            "CANCELLED", "已撤销");

    private final JwBindingMapper jwBindingMapper;
    private final ScheduleCourseMapper courseMapper;
    private final ServiceRequestMapper requestMapper;
    private final ServiceItemMapper itemMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AiAssistantResolved resolveSchedule(Long userId, Integer dayOfWeek) {
        JwBindingEntity binding = jwBindingMapper.findByUserId(userId);
        List<ScheduleCourseEntity> courses = courseMapper.selectByUserId(userId);

        String semesterCode;
        if (binding != null && binding.getLastSemesterCode() != null) {
            semesterCode = binding.getLastSemesterCode();
        } else {
            semesterCode = courses.stream()
                    .map(ScheduleCourseEntity::getSemesterCode)
                    .filter(java.util.Objects::nonNull)
                    .max(String::compareTo)
                    .orElse(null);
        }

        if (semesterCode == null) {
            return AiAssistantResolved.of("该用户尚未同步过课表，无法提供课表信息。",
                    List.of(new AiActionResponse("SCHEDULE", "去查看/同步课表", "SCHEDULE-VIEW", null,
                            "/pages/schedule/index")));
        }

        final String code = semesterCode;
        List<ScheduleCourseEntity> current = courses.stream()
                .filter(course -> code.equals(course.getSemesterCode()))
                .sorted(Comparator.comparing(ScheduleCourseEntity::getDayOfWeek)
                        .thenComparing(ScheduleCourseEntity::getStartSection))
                .toList();

        List<ScheduleCourseEntity> matched;
        String scopeText;
        if (dayOfWeek != null) {
            final int day = dayOfWeek;
            matched = current.stream().filter(course -> course.getDayOfWeek() != null && course.getDayOfWeek() == day).toList();
            scopeText = "星期" + "一二三四五六日".charAt(day - 1);
        } else {
            matched = current;
            scopeText = "本周";
        }

        if (matched.isEmpty()) {
            String text = current.isEmpty()
                    ? "该学期暂未导入课程数据（可能是假期或尚未排课）。"
                    : scopeText + "没有课。";
            return AiAssistantResolved.of(text,
                    List.of(new AiActionResponse("SCHEDULE", "去查看课表", "SCHEDULE-VIEW", null,
                            "/pages/schedule/index")));
        }

        StringBuilder context = new StringBuilder();
        context.append("用户课表（").append(scopeText).append("）。\n");
        for (ScheduleCourseEntity course : matched) {
            context.append("- ").append(course.getCourseName())
                    .append("｜老师:").append(nullTo(course.getTeacher()))
                    .append("｜地点:").append(nullTo(course.getLocation()))
                    .append("｜第").append(course.getStartSection()).append("-").append(course.getEndSection()).append("节")
                    .append("｜周次:").append(nullTo(course.getWeeks()))
                    .append(parityText(course.getWeekParity()))
                    .append("\n");
        }
        return AiAssistantResolved.of(context.toString(),
                List.of(new AiActionResponse("SCHEDULE", "去查看课表", "SCHEDULE-VIEW", null,
                        "/pages/schedule/index")));
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