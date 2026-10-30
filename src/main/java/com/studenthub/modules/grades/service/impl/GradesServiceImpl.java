package com.studenthub.modules.grades.service.impl;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.jw.KingosoftGradesClient;
import com.studenthub.integration.jw.KingosoftGradesClient.JwGradeCourse;
import com.studenthub.integration.jw.KingosoftGradesClient.JwGradeReport;
import com.studenthub.integration.jw.KingosoftGradesClient.JwGradeSummary;
import com.studenthub.integration.jw.KingosoftGradesClient.JwGradeTerm;
import com.studenthub.modules.grades.controller.dto.GradeReportResponse;
import com.studenthub.modules.grades.service.GradesService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.jwauth.security.JwSessionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 学业成绩服务：复用教务托管会话实时抓取，5 分钟内重复请求走内存缓存（成绩变动频率低）。
 * 会话失效时同步把 jw_binding 翻转为 EXPIRED，与课表导入的失败语义保持一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradesServiceImpl implements GradesService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int CACHE_MAX_ENTRIES = 500;

    private final JwBindingMapper jwBindingMapper;
    private final JwSessionStore jwSessionStore;
    private final KingosoftGradesClient gradesClient;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public GradeReportResponse report(AuthenticatedUser user, String range, String scoreType, String xn, String xq) {
        JwBindingEntity binding = requireAuthenticatedBinding(user.userId());
        String cacheKey = user.userId() + "|" + nullSafe(range) + "|" + nullSafe(scoreType)
                + "|" + nullSafe(xn) + "|" + nullSafe(xq);
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            return cached.report();
        }

        Optional<JwSessionStore.JwSession> session = jwSessionStore.find(user.userId());
        if (session.isEmpty()) {
            markExpired(binding);
            throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
        }

        JwGradeReport report;
        try {
            report = gradesClient.fetchGrades(session.get().cookieHeader(), range, scoreType, xn, xq);
        } catch (BusinessException exception) {
            // 远端会话失效时同步翻转认证状态（须在事务外，异常不把标记回滚）
            if (ErrorCode.JW_SESSION_EXPIRED.getCode().equals(exception.getCode())) {
                markExpired(binding);
            }
            throw exception;
        }

        GradeReportResponse response = toResponse(report);
        putCache(cacheKey, response);
        return response;
    }

    private GradeReportResponse toResponse(JwGradeReport report) {
        List<GradeReportResponse.Term> terms = report.terms().stream().map(this::toTerm).toList();
        return new GradeReportResponse(report.range(), report.scoreType(), terms, toSummary(report.summary()));
    }

    private GradeReportResponse.Term toTerm(JwGradeTerm term) {
        List<GradeReportResponse.Course> courses = term.courses().stream().map(this::toCourse).toList();
        return new GradeReportResponse.Term(term.termName(), term.courseCount(), term.credits(), courses);
    }

    private GradeReportResponse.Course toCourse(JwGradeCourse course) {
        return new GradeReportResponse.Course(course.sequence(), course.courseCode(), course.courseName(),
                course.credit(), course.hours(), course.category(), course.courseType(), course.examType(),
                course.score(), course.earnedCredits(), course.gradePoint(), course.creditGradePoint(),
                course.remark(), course.passed());
    }

    private GradeReportResponse.Summary toSummary(JwGradeSummary summary) {
        return new GradeReportResponse.Summary(summary.courseCount(), summary.credits(),
                summary.earnedCredits(), summary.earnedGradePoints(), summary.earnedCreditGradePoints(),
                summary.averageGradePoint(), summary.averageScore(), summary.weightedAverageScore());
    }

    private void putCache(String key, GradeReportResponse response) {
        if (cache.size() >= CACHE_MAX_ENTRIES) {
            long now = System.currentTimeMillis();
            cache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }
        cache.put(key, new CacheEntry(response, System.currentTimeMillis() + CACHE_TTL.toMillis()));
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
            // 成绩查询仅面向已认证的学生
            throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
        }
        return binding;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private record CacheEntry(GradeReportResponse report, long expiresAt) {
    }
}
