package com.studenthub.modules.schedule.job;

import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.schedule.entity.ScheduleClassCourseEntity;
import com.studenthub.modules.schedule.mapper.ScheduleClassCourseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 班级课表缓存的写事务：覆盖写入与变更通知在同一条事务内，失败整体回滚 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleClassCacheWriter {

    private final ScheduleClassCourseMapper classCourseMapper;
    private final NotificationService notificationService;

    @Transactional(rollbackFor = Exception.class)
    public void applyChange(String className, String semesterCode,
                            List<ScheduleClassCourseEntity> classCourses,
                            List<JwBindingEntity> bindings,
                            Set<Long> sourceUserIds,
                            int addedCount, int removedCount) {
        // 覆盖写入
        classCourseMapper.deleteByClassAndSemester(className, semesterCode);
        if (!classCourses.isEmpty()) {
            classCourseMapper.insertBatch(classCourses);
        }

        // 变更通知（同班非采样源同学）
        String content = buildContent(addedCount, removedCount);
        for (JwBindingEntity binding : bindings) {
            if (sourceUserIds.contains(binding.getUserId())) {
                continue;
            }
            try {
                notificationService.send(new NotificationSendCommand(
                        binding.getUserId(), "SYSTEM", "课表更新提醒", content,
                        "SCHEDULE", null));
            } catch (Exception exception) {
                // 单条通知失败不阻断同步结果
                log.warn("课表变更通知发送失败, userId={}", binding.getUserId());
            }
        }
    }

    private String buildContent(int addedCount, int removedCount) {
        StringBuilder content = new StringBuilder("你的班级课表有更新：");
        if (addedCount == 0 && removedCount == 0) {
            return content.append("课程安排已刷新，点击查看最新课表。").toString();
        }
        if (addedCount > 0) {
            content.append("新增 ").append(addedCount).append(" 门课");
        }
        if (removedCount > 0) {
            if (addedCount > 0) {
                content.append("，");
            }
            content.append("调整 ").append(removedCount).append(" 门课");
        }
        return content.append("。点击查看最新课表。").toString();
    }
}