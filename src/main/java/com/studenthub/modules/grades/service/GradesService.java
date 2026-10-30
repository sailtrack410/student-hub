package com.studenthub.modules.grades.service;

import com.studenthub.modules.grades.controller.dto.GradeReportResponse;
import com.studenthub.modules.identity.security.AuthenticatedUser;

/**
 * 学业成绩：基于教务托管会话实时拉取（短缓存），只读，不落库。
 */
public interface GradesService {

    /**
     * @param range     ALL / YEAR / TERM，缺省 ALL
     * @param scoreType EFFECTIVE / RAW，缺省 EFFECTIVE
     * @param xn        学年起始年（YEAR/TERM 使用）
     * @param xq        学期下拉值（TERM 使用）
     */
    GradeReportResponse report(AuthenticatedUser user, String range, String scoreType, String xn, String xq);
}
