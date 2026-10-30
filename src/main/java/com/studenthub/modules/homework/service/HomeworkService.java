package com.studenthub.modules.homework.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.homework.controller.dto.AdminHomeworkPageQuery;
import com.studenthub.modules.homework.controller.dto.AdminHomeworkResponse;
import com.studenthub.modules.homework.controller.dto.CreateHomeworkRequest;
import com.studenthub.modules.homework.controller.dto.HomeworkDetailResponse;
import com.studenthub.modules.homework.controller.dto.HomeworkPageQuery;
import com.studenthub.modules.homework.controller.dto.HomeworkSummaryResponse;
import com.studenthub.modules.homework.controller.dto.SubmitHomeworkRequest;
import com.studenthub.modules.identity.security.AuthenticatedUser;

/**
 * 作业中心：管理端发布/分页/关闭作业，App 端按校区查看作业与在线提交。
 */
public interface HomeworkService {

    /** App：只返回当前用户校区下 PUBLISHED 作业，并标记当前用户是否已提交 */
    PageResult<HomeworkSummaryResponse> pageForApp(AuthenticatedUser user, HomeworkPageQuery query);

    /** App：作业详情（同校区可见，含当前用户提交内容） */
    HomeworkDetailResponse detailForApp(AuthenticatedUser user, Long homeworkId);

    /** App：提交作业；重复提交更新原记录，关闭或已过截止时间时拒绝 */
    HomeworkDetailResponse submit(AuthenticatedUser user, Long homeworkId, SubmitHomeworkRequest request);

    /** 管理端：发布作业 */
    AdminHomeworkResponse create(AuthenticatedUser operator, CreateHomeworkRequest request);

    /** 管理端：分页查询作业（可按状态过滤，默认当前账号校区） */
    PageResult<AdminHomeworkResponse> pageForAdmin(AuthenticatedUser operator, AdminHomeworkPageQuery query);

    /** 管理端：关闭作业；已关闭时幂等返回 */
    AdminHomeworkResponse close(AuthenticatedUser operator, Long homeworkId);
}
