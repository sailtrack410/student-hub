package com.studenthub.modules.push.content.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.push.content.controller.dto.CreatorCertifyRequest;
import com.studenthub.modules.push.content.controller.dto.CreatorPageQuery;
import com.studenthub.modules.push.content.controller.dto.CreatorPenaltyRequest;
import com.studenthub.modules.push.content.controller.dto.CreatorPenaltyResponse;
import com.studenthub.modules.push.content.controller.dto.CreatorProfileRequest;
import com.studenthub.modules.push.content.controller.dto.CreatorProfileResponse;

/**
 * 创作者运营（C10）：认证、等级、禁言/封禁、申诉。
 */
public interface CreatorService {

    PageResult<CreatorProfileResponse> page(CreatorPageQuery query);

    CreatorProfileResponse detail(Long id);

    CreatorProfileResponse createProfile(CreatorProfileRequest request);

    CreatorProfileResponse updateProfile(Long id, CreatorProfileRequest request);

    CreatorProfileResponse certify(Long id, CreatorCertifyRequest request);

    CreatorPenaltyResponse createPenalty(Long id, CreatorPenaltyRequest request);

    CreatorPenaltyResponse revokePenalty(Long penaltyId);

    CreatorPenaltyResponse handleAppeal(Long penaltyId, boolean approve, String result);

    /** 把到期处罚置为 EXPIRED 并重算创作者状态；返回处理条数。 */
    int expireStalePenalties();
}
