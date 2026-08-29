package com.studenthub.modules.schedule.service;

import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.schedule.controller.dto.ScheduleImportResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleViewResponse;

import java.util.List;

public interface ScheduleService {

    /** 从教务系统导入当前学生个人课表，并同步一份到班级缓存供同学共享 */
    ScheduleImportResponse importSchedule(AuthenticatedUser user);

    /** 查询已导入的个人课表 */
    ScheduleViewResponse view(AuthenticatedUser user);
}