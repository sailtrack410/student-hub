package com.studenthub.modules.schedule.service;

import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.schedule.controller.dto.ScheduleImportResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleNextCourseResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleViewResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleWeekViewResponse;

import java.util.List;

public interface ScheduleService {

    /** 从教务系统导入个人课表；semesterCode 为空时按校历选择当前学期 */
    ScheduleImportResponse importSchedule(AuthenticatedUser user, String semesterCode);

    /** 查询已导入的个人课表；semesterCode 为空时返回最近同步学期 */
    ScheduleViewResponse view(AuthenticatedUser user, String semesterCode);

    /** 按周展开课表：服务端解析周次串成日期与时间段；个人课表缺失时回落班级缓存 */
    ScheduleWeekViewResponse weekView(AuthenticatedUser user, String semesterCode, Integer week);

    /** 首页 BFF：当前/下一节有课信息（含数据来源标识） */
    ScheduleNextCourseResponse nextCourse(AuthenticatedUser user);

    /** 本学期调课通知列表（noticeTime 倒序），不含 raw_text */
    List<com.studenthub.modules.schedule.controller.dto.ScheduleAdjustmentItem> adjustments(String semesterCode);
}