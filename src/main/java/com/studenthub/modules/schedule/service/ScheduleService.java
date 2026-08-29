package com.studenthub.modules.schedule.service;

import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.schedule.controller.dto.ScheduleImportResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleViewResponse;

import java.util.List;

public interface ScheduleService {

    /** 从教务系统导入个人课表；semesterCode 为空时按校历选择当前学期 */
    ScheduleImportResponse importSchedule(AuthenticatedUser user, String semesterCode);

    /** 查询已导入的个人课表；semesterCode 为空时返回最近同步学期 */
    ScheduleViewResponse view(AuthenticatedUser user, String semesterCode);
}