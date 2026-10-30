package com.studenthub.modules.community.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.community.controller.dto.AdminOfficialAccountPageQuery;
import com.studenthub.modules.community.controller.dto.AdminOfficialAccountRequest;
import com.studenthub.modules.community.controller.dto.AdminOfficialAccountResponse;

/**
 * 管理端官方账号：列表、创建、编辑、启停。
 * 账号类型约束：官方抓取号 OFFICIAL_AUTO、人工运营号 OFFICIAL_HUMAN、广告服务号 AD_SERVICE。
 */
public interface CommunityOfficialAccountService {

    PageResult<AdminOfficialAccountResponse> page(AdminOfficialAccountPageQuery query);

    AdminOfficialAccountResponse create(AdminOfficialAccountRequest request);

    AdminOfficialAccountResponse update(Long userId, AdminOfficialAccountRequest request);

    AdminOfficialAccountResponse updateStatus(Long userId, String status);
}
