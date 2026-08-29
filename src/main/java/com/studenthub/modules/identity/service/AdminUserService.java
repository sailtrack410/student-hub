package com.studenthub.modules.identity.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.identity.controller.dto.AdminRoleResponse;
import com.studenthub.modules.identity.controller.dto.AdminUserPageQuery;
import com.studenthub.modules.identity.controller.dto.AdminUserSummaryResponse;
import com.studenthub.modules.identity.controller.dto.WorkerCandidateResponse;

import java.util.List;

public interface AdminUserService {

    PageResult<AdminUserSummaryResponse> page(AdminUserPageQuery query);

    /**
     * 启用/停用账号；停用会立即让该用户全部在线会话失效。
     */
    void updateStatus(Long operatorId, Long userId, String status);

    /**
     * 授权审核：APPROVE 置为 AUTHORIZED，REJECT 置为 REJECTED，并原地刷新在线会话。
     */
    void reviewAuthorization(Long operatorId, Long userId, String action, String reason);

    /**
     * 整体覆盖用户角色，并联动 identity_type/authorization_status（授予服务角色时升为
     * LOGISTICS/AUTHORIZED，撤销后回落），随后原地刷新在线会话。
     */
    AdminUserSummaryResponse updateRoles(Long operatorId, Long userId, List<Long> roleIds);

    List<WorkerCandidateResponse> workerCandidates(String keyword);

    List<AdminRoleResponse> listRoles();
}
