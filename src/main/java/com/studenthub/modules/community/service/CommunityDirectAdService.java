package com.studenthub.modules.community.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.community.controller.dto.AdminDirectAdPageQuery;
import com.studenthub.modules.community.controller.dto.AdminDirectAdRequest;
import com.studenthub.modules.community.controller.dto.AdminDirectAdResponse;
import com.studenthub.modules.community.controller.dto.AdminDirectAdReviewRequest;
import com.studenthub.modules.community.controller.dto.AdminDirectAdStatsResponse;

import java.time.LocalDateTime;

/**
 * 管理端官方直投广告：增删改查、上下架、优先级与统计。
 * 广告不绑定社区账号；统计中外部浏览器/DEEPLINK 落地类型不可测跳出，跳出率返回 null。
 */
public interface CommunityDirectAdService {

    PageResult<AdminDirectAdResponse> page(AdminDirectAdPageQuery query);

    AdminDirectAdResponse detail(Long adId);

    AdminDirectAdResponse create(AdminDirectAdRequest request);

    AdminDirectAdResponse update(Long adId, AdminDirectAdRequest request);

    AdminDirectAdResponse updateStatus(Long adId, String status);

    /** 提交审核：DRAFT/REJECTED -> PENDING；PENDING 幂等返回。 */
    AdminDirectAdResponse submit(Long adId);

    /** 审核：仅 PENDING 可审；APPROVE/REJECT，驳回必须填写原因。 */
    AdminDirectAdResponse review(Long adId, AdminDirectAdReviewRequest request);

    void delete(Long adId);

    AdminDirectAdStatsResponse stats(Long adId, Integer bounceThresholdSeconds,
                                     LocalDateTime startAt, LocalDateTime endAt);
}
