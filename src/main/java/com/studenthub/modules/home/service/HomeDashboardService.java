package com.studenthub.modules.home.service;

import com.studenthub.modules.home.controller.dto.HomeDashboardResponse;
import com.studenthub.modules.identity.security.AuthenticatedUser;

/**
 * 首页 BFF（API需求清单 4.1）：单请求聚合学业条/下一节课/公告/进行中申请/未读数。
 */
public interface HomeDashboardService {

    HomeDashboardResponse dashboard(AuthenticatedUser user);
}
