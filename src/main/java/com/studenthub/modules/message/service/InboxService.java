package com.studenthub.modules.message.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.message.controller.dto.CategorySummaryResponse;
import com.studenthub.modules.message.controller.dto.InboxItemResponse;

public interface InboxService {

    /** 三路会话聚合：系统通知(折叠 1 条) + 私信(每对端 1 条) + AI 助手(每会话 1 条)，按最后消息时间倒序分页 */
    PageResult<InboxItemResponse> inbox(AuthenticatedUser user, int pageNum, int pageSize);

    /** 消息中心四大分类未读数聚合（需求清单 5.1）：流程通知/互动(社区通知+私信)/公告/AI 待办 */
    CategorySummaryResponse categorySummary(AuthenticatedUser user);
}
