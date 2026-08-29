package com.studenthub.modules.schedule.job;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.jw.KingosoftScheduleClient;
import com.studenthub.integration.jw.KingosoftScheduleClient.JwCourseEntry;
import com.studenthub.integration.jw.KingosoftScheduleClient.JwSchedulePage;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.jwauth.security.JwSessionStore;
import com.studenthub.modules.schedule.config.ScheduleSyncProperties;
import com.studenthub.modules.schedule.entity.ScheduleClassCourseEntity;
import com.studenthub.modules.schedule.mapper.ScheduleClassCourseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 课表定时同步：每个班级采样若干“当前有托管会话”的同学的课表，
 * 仅保留多人共有的课程（排除单人选修）写入班级缓存，差异变更推送给同班同学。
 *
 * <p>触发入口 {@link ScheduleSyncTimer}，按配置 cron 执行；班内采样随机、每班随机错峰。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleSyncWorker {

    /** 每班最多采样的源用户数（不足则有多少用多少） */
    private static final int SAMPLE_LIMIT = 3;

    private final JwBindingMapper jwBindingMapper;
    private final JwSessionStore jwSessionStore;
    private final KingosoftScheduleClient scheduleClient;
    private final ScheduleClassCourseMapper classCourseMapper;
    private final ScheduleClassCacheWriter cacheWriter;
    private final ScheduleSyncProperties properties;

    /** 单个班级的同步任务（并发度由线程池控制，见 @Async 配置） */
    @Async("scheduleSyncExecutor")
    public void syncClassAsync(String className, List<JwBindingEntity> bindings) {
        try {
            if (bindings.isEmpty()) {
                return;
            }
            int stagger = properties.getStaggerMaxSeconds();
            if (stagger > 0) {
                sleep(ThreadLocalRandom.current().nextInt(stagger + 1), TimeUnit.SECONDS);
            }
            syncClass(className, bindings);
        } catch (Exception exception) {
            log.warn("班级课表同步失败, className={}, msg={}", className, exception.getMessage());
        }
    }

    private void syncClass(String className, List<JwBindingEntity> bindings) {
        // 1. 随机挑选有托管会话的采样源
        List<JwBindingEntity> shuffled = new ArrayList<>(bindings);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        List<Sample> samples = new ArrayList<>();
        for (JwBindingEntity binding : shuffled) {
            if (samples.size() >= SAMPLE_LIMIT) {
                break;
            }
            Optional<JwSessionStore.JwSession> session = jwSessionStore.find(binding.getUserId());
            if (session.isEmpty()) {
                continue;
            }
            try {
                JwSchedulePage page = scheduleClient.fetchSchedulePage(session.get().cookieHeader(), null);
                samples.add(new Sample(binding, page));
            } catch (BusinessException exception) {
                if (ErrorCode.JW_SESSION_EXPIRED.getCode().equals(exception.getCode())) {
                    // 会话已被学校服务器拒绝：按约定标记已失效并换下一个采样源
                    markExpired(binding);
                }
                log.info("采样课表失败, userId={}, className={}, msg={}",
                        binding.getUserId(), className, exception.getMessage());
            } catch (Exception exception) {
                log.info("采样课表失败, userId={}, className={}, msg={}",
                        binding.getUserId(), className, exception.getMessage());
            }
        }
        if (samples.isEmpty()) {
            log.info("班级无可用采样源, className={}", className);
            return;
        }

        String semesterCode = samples.get(0).page.semesterCode();
        String semesterName = samples.get(0).page.semesterName();

        // 2. 聚合：同一课程签名 → 覆盖它的不同用户数（排除单人选修）
        Map<String, CourseAggregate> aggregates = new LinkedHashMap<>();
        for (Sample sample : samples) {
            for (String signature : distinctSignatures(sample.page.courses())) {
                aggregates.computeIfAbsent(signature, CourseAggregate::new)
                        .addUser(sample.binding.getUserId());
            }
        }
        List<ScheduleClassCourseEntity> classCourses = new ArrayList<>();
        for (CourseAggregate aggregate : aggregates.values()) {
            boolean keep = aggregate.userCount() >= 2 || samples.size() == 1;
            if (keep) {
                classCourses.add(aggregate.toEntity(className, semesterCode, semesterName));
            }
        }
        log.info("班级采样结果, className={}, 采样={}, 保留课程={}",
                className, samples.size(), classCourses.size());

        // 3. diff
        List<ScheduleClassCourseEntity> existing = classCourseMapper.selectByClassAndSemester(className, semesterCode);
        Set<String> before = existing.stream().map(CourseAggregate::signatureOf).collect(Collectors.toSet());
        Set<String> after = classCourses.stream().map(CourseAggregate::signatureOf).collect(Collectors.toSet());
        Set<String> added = new HashSet<>(after);
        added.removeAll(before);
        Set<String> removed = new HashSet<>(before);
        removed.removeAll(after);
        if (added.isEmpty() && removed.isEmpty()) {
            return;
        }

        // 4. 覆盖班级缓存 + 通知（保证事务一致性）
        Set<Long> sourceUserIds = samples.stream().map(s -> s.binding.getUserId()).collect(Collectors.toSet());
        cacheWriter.applyChange(className, semesterCode, classCourses, bindings,
                sourceUserIds, added.size(), removed.size());
        log.info("班级课表已更新, className={}, +{} -{}", className, added.size(), removed.size());
    }

    private List<String> distinctSignatures(List<JwCourseEntry> courses) {
        return courses.stream().map(CourseAggregate::signatureOf).distinct().toList();
    }

    private void markExpired(JwBindingEntity binding) {
        JwBindingEntity update = new JwBindingEntity();
        update.setId(binding.getId());
        update.setAuthStatus("EXPIRED");
        update.setVersion(binding.getVersion());
        jwBindingMapper.updateById(update);
    }

    private void sleep(long amount, TimeUnit unit) {
        try {
            unit.sleep(amount);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** 采样源与其课表 */
    private record Sample(JwBindingEntity binding, JwSchedulePage page) {
    }

    /**
     * 课程聚合：签名相同的课程合并，统计覆盖它的不同用户数，
     * 可转回班级课表实体。
     */
    private static final class CourseAggregate {
        private final String courseName;
        private final String teacher;
        private final String location;
        private final int dayOfWeek;
        private final int startSection;
        private final int endSection;
        private final String weeks;
        private final int weekParity;
        private final Set<Long> users = new HashSet<>();

        CourseAggregate(String signature) {
            String[] parts = signature.split("\\|", -1);
            this.courseName = parts[0];
            this.teacher = parts[1];
            this.location = parts[2];
            this.dayOfWeek = Integer.parseInt(parts[3]);
            this.startSection = Integer.parseInt(parts[4]);
            this.endSection = Integer.parseInt(parts[5]);
            this.weeks = parts[6];
            this.weekParity = Integer.parseInt(parts[7]);
        }

        static String signatureOf(JwCourseEntry course) {
            return course.courseName() + "|" + course.teacher() + "|" + course.location() + "|"
                    + course.dayOfWeek() + "|" + course.startSection() + "|" + course.endSection() + "|"
                    + course.weeks() + "|" + course.weekParity();
        }

        static String signatureOf(ScheduleClassCourseEntity entity) {
            return entity.getCourseName() + "|" + entity.getTeacher() + "|" + entity.getLocation() + "|"
                    + entity.getDayOfWeek() + "|" + entity.getStartSection() + "|" + entity.getEndSection() + "|"
                    + entity.getWeeks() + "|" + entity.getWeekParity();
        }

        void addUser(Long userId) {
            users.add(userId);
        }

        int userCount() {
            return users.size();
        }

        ScheduleClassCourseEntity toEntity(String className, String semesterCode, String semesterName) {
            ScheduleClassCourseEntity entity = new ScheduleClassCourseEntity();
            entity.setId(IdWorker.getId());
            entity.setClassCode(className);
            entity.setCreatedBy(0L);
            entity.setUpdatedBy(0L);
            entity.setSemesterCode(semesterCode);
            entity.setSemesterName(semesterName);
            entity.setCourseName(courseName);
            entity.setTeacher(teacher);
            entity.setLocation(location);
            entity.setDayOfWeek(dayOfWeek);
            entity.setStartSection(startSection);
            entity.setEndSection(endSection);
            entity.setWeeks(weeks);
            entity.setWeekParity(weekParity);
            return entity;
        }
    }
}