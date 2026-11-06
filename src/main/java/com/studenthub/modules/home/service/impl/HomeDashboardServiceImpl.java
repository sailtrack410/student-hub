package com.studenthub.modules.home.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.modules.home.controller.dto.HomeDashboardResponse;
import com.studenthub.modules.home.mapper.CampusNameMapper;
import com.studenthub.modules.home.service.HomeDashboardService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.message.service.InboxService;
import com.studenthub.modules.schedule.controller.dto.ScheduleNextCourseResponse;
import com.studenthub.modules.schedule.service.ScheduleService;
import com.studenthub.modules.screen.controller.dto.AppAnnouncementResponse;
import com.studenthub.modules.screen.service.ScreenAdminService;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 首页 BFF 实现（API需求清单 4.1）：所有子块独立兜底，单项异常不影响整体返回。
 * 聚合源：课表 nextCourse（已含周次）、APP 渠道最新 1 条公告、最新进行中申请（SUBMITTED/PROCESSING）、
 * 消息中心未读汇总。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeDashboardServiceImpl implements HomeDashboardService {

    private static final Set<String> IN_PROGRESS_STATUSES = Set.of("SUBMITTED", "PROCESSING");

    private final CampusNameMapper campusNameMapper;
    private final ScheduleService scheduleService;
    private final ScreenAdminService screenAdminService;
    private final ServiceRequestMapper serviceRequestMapper;
    private final WorkOrderMapper workOrderMapper;
    private final InboxService inboxService;

    @Override
    public HomeDashboardResponse dashboard(AuthenticatedUser user) {
        LocalDate today = LocalDate.now();
        return new HomeDashboardResponse(
                greeting(user),
                academicInfo(user, today),
                nextSchedule(user),
                latestAnnouncement(),
                inProgressRequest(user),
                unreadCount(user));
    }

    private String greeting(AuthenticatedUser user) {
        int hour = LocalDateTime.now().getHour();
        String period = hour < 6 ? "夜深了" : hour < 12 ? "早安" : hour < 14 ? "午安" : hour < 18 ? "下午好" : "晚上好";
        return period + "，" + user.displayName();
    }

    private HomeDashboardResponse.AcademicInfo academicInfo(AuthenticatedUser user, LocalDate today) {
        String campusName = null;
        try {
            campusName = campusNameMapper.nameOf(user.campusId());
        } catch (RuntimeException exception) {
            log.warn("首页 BFF 校区名查询失败: {}", exception.getMessage());
        }
        String weekDay = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA)
                .replace("星期", "周");
        int week = 0;
        try {
            week = scheduleService.nextCourse(user).week();
        } catch (RuntimeException exception) {
            log.warn("首页 BFF 周次查询失败: {}", exception.getMessage());
        }
        String formatted = (campusName == null ? "" : campusName + " · ") + weekDay
                + (week > 0 ? " 第" + week + "周" : "");
        return new HomeDashboardResponse.AcademicInfo(campusName, weekDay, week, formatted);
    }

    private HomeDashboardResponse.NextSchedule nextSchedule(AuthenticatedUser user) {
        try {
            ScheduleNextCourseResponse next = scheduleService.nextCourse(user);
            ScheduleNextCourseResponse.CourseView view = next.current() != null ? next.current() : next.next();
            if (!next.hasCourse() || view == null) {
                return null;
            }
            boolean ongoing = next.current() != null;
            return new HomeDashboardResponse.NextSchedule(view.courseName(), view.teacher(), view.location(),
                    view.startSection(), view.endSection(), view.timeRange(), ongoing ? "ONGOING" : "UPCOMING");
        } catch (RuntimeException exception) {
            log.warn("首页 BFF 下一节课查询失败: {}", exception.getMessage());
            return null;
        }
    }

    private HomeDashboardResponse.AnnouncementCard latestAnnouncement() {
        try {
            return screenAdminService.pageAppAnnouncements(1, 1).getRecords().stream().findFirst()
                    .map(this::toAnnouncementCard).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("首页 BFF 公告查询失败: {}", exception.getMessage());
            return null;
        }
    }

    private HomeDashboardResponse.AnnouncementCard toAnnouncementCard(AppAnnouncementResponse announcement) {
        return new HomeDashboardResponse.AnnouncementCard(announcement.id(), announcement.title(),
                announcement.typeText(), "/pages/announcement/detail?id=" + announcement.id());
    }

    private HomeDashboardResponse.InProgressRequest inProgressRequest(AuthenticatedUser user) {
        List<ServiceRequestEntity> rows = serviceRequestMapper.selectList(
                new LambdaQueryWrapper<ServiceRequestEntity>()
                        .eq(ServiceRequestEntity::getRequesterId, user.userId())
                        .in(ServiceRequestEntity::getStatus, IN_PROGRESS_STATUSES)
                        .orderByDesc(ServiceRequestEntity::getSubmittedAt)
                        .last("LIMIT 1"));
        if (rows.isEmpty()) {
            return null;
        }
        ServiceRequestEntity request = rows.get(0);
        WorkOrderEntity order = com.studenthub.modules.servicerequest.service.RequestBusinessView.repair(request)
                ? workOrderMapper.findByRequestId(request.getId()) : null;
        if (order != null && Boolean.TRUE.equals(order.getFlowArchived())) order = null;
        return new HomeDashboardResponse.InProgressRequest(request.getId(),
                order == null ? null : order.getOrderNo(),
                request.getItemName() == null ? request.getTitle() : request.getItemName(),
                request.getStatus(),
                request.getSubmittedAt() == null ? null : request.getSubmittedAt().toString(),
                com.studenthub.modules.servicerequest.service.RequestBusinessView.flowType(request),
                com.studenthub.modules.servicerequest.service.RequestBusinessView.status(request, order == null ? null : order.getStatus()),
                com.studenthub.modules.servicerequest.service.RequestBusinessView.label(request, order == null ? null : order.getStatus()));
    }

    private long unreadCount(AuthenticatedUser user) {
        try {
            return inboxService.categorySummary(user).totalUnread();
        } catch (RuntimeException exception) {
            log.warn("首页 BFF 未读数查询失败: {}", exception.getMessage());
            return 0;
        }
    }
}
