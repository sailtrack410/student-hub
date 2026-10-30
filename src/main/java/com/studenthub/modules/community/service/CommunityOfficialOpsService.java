package com.studenthub.modules.community.service;

import com.studenthub.modules.community.controller.dto.AdminOfficialPinRequest;
import com.studenthub.modules.community.controller.dto.AdminOfficialPublishRequest;
import com.studenthub.modules.community.controller.dto.AdminOfficialPublishResponse;
import com.studenthub.modules.community.controller.dto.AdminOfficialReplyRequest;
import com.studenthub.modules.community.controller.dto.AdminOfficialReplyResponse;

/**
 * 官方号运营（C12）：帖子置顶、指定官方账号回复帖子、官方账号代发帖子。
 *
 * <p>约束：官方账号必须是 OFFICIAL_AUTO（官网抓取号）或 OFFICIAL_HUMAN（人工运营号），
 * 且与操作校区一致、账号启用；AD_SERVICE 广告服务号不得以官方身份回复/代发。
 * 所有写操作由管理端审计 AOP 自动留痕（操作人/资源/参数）。</p>
 */
public interface CommunityOfficialOpsService {

    /** 置顶/取消置顶帖子；置顶不改变帖子状态，仅影响信息流排序 */
    void pinPost(Long postId, AdminOfficialPinRequest request);

    /** 指定官方账号对帖子发官方回复（生成一条官方评论并通知楼主） */
    AdminOfficialReplyResponse replyAsOfficial(Long postId, AdminOfficialReplyRequest request);

    /** 以官方账号身份代发社区帖子（命中屏蔽词时拒绝并提示，不静默隐藏官方内容） */
    AdminOfficialPublishResponse publishAsOfficial(AdminOfficialPublishRequest request);
}
