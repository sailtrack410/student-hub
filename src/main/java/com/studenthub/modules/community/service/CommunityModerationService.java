package com.studenthub.modules.community.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.community.controller.dto.AdminCommunityContentActionRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityReportHandleRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityReportPageQuery;
import com.studenthub.modules.community.controller.dto.AdminCommunityReportResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityReportSummaryResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunitySanctionRequest;
import com.studenthub.modules.community.controller.dto.CreateCommunityReportRequest;
import com.studenthub.modules.identity.security.AuthenticatedUser;

/**
 * 社区内容治理：举报提交与处置、内容下架恢复、用户处罚。
 * 举报保存内容快照，处置与处罚均留审计痕迹（handled_by / report_id）。
 */
public interface CommunityModerationService {

    /** 小程序侧提交举报（帖子/评论/私信/用户），含重复举报与每日次数限制 */
    void submitReport(AuthenticatedUser user, CreateCommunityReportRequest request);

    /** 管理端举报队列分页 */
    PageResult<AdminCommunityReportResponse> pageReports(AdminCommunityReportPageQuery query);

    /** 管理端治理概览（举报量、处罚数） */
    AdminCommunityReportSummaryResponse summary();

    /** 管理端处理举报：成立可同时下架内容并处罚作者，驳回仅记录结论 */
    void handleReport(Long reportId, AdminCommunityReportHandleRequest request);

    /** 治理端直接下架内容（无需举报，用于日常巡检） */
    void hideContent(String targetType, Long targetId, AdminCommunityContentActionRequest request);

    /** 治理端恢复被下架内容 */
    void restoreContent(String targetType, Long targetId, AdminCommunityContentActionRequest request);

    /** 治理端处罚用户（限制发言/禁止访问） */
    void sanctionUser(Long userId, AdminCommunitySanctionRequest request);

    /** 撤销处罚（用于申诉通过后的提前解除） */
    void revokeSanction(Long sanctionId);

    /** 校验当前用户未被限制发言，被限制时抛出 COMMUNITY_MUTED */
    void requireNotMuted(Long userId);
}
