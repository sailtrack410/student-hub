package com.studenthub.modules.community.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.community.controller.dto.AdminAdvertiserPageQuery;
import com.studenthub.modules.community.controller.dto.AdminAdvertiserRequest;
import com.studenthub.modules.community.controller.dto.AdminAdvertiserResponse;

/**
 * 管理端社区广告主：按管理端校区上下文隔离的 CRUD 与启停。
 * 广告主停用只影响新建广告的选择，不回溯修改已投放广告。
 */
public interface CommunityAdvertiserService {

    PageResult<AdminAdvertiserResponse> page(AdminAdvertiserPageQuery query);

    AdminAdvertiserResponse detail(Long advertiserId);

    AdminAdvertiserResponse create(AdminAdvertiserRequest request);

    AdminAdvertiserResponse update(Long advertiserId, AdminAdvertiserRequest request);

    AdminAdvertiserResponse updateStatus(Long advertiserId, String status);

    void delete(Long advertiserId);
}
