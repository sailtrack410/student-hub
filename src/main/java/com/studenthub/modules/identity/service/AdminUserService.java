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
     * U04 增强：写入审核备注/审核人/审核时间、独立审核记录，并向申请人发送站内通知。
     *
     * @param reasonCode 拒绝原因模板编码（可空；与 reason 至少提供一项，否则 400）
     * @param reason     自定义备注/补充说明
     */
    void reviewAuthorization(Long operatorId, Long userId, String action, String reasonCode, String reason);

    /**
     * 整体覆盖用户角色，并联动 identity_type/authorization_status（授予服务角色时升为
     * LOGISTICS/AUTHORIZED，撤销后回落），随后原地刷新在线会话。
     */
    AdminUserSummaryResponse updateRoles(Long operatorId, Long userId, List<Long> roleIds);

    /**
     * 整体覆盖用户角色绑定，支持为每个角色指定组织与数据范围。
     */
    AdminUserSummaryResponse updateRoleBindings(Long operatorId, Long userId,
                                                List<com.studenthub.modules.identity.controller.dto.AdminUserRoleBindingsRequest.Binding> bindings);

    /**
     * 设置/取消官方账号；官方账号在社区展示「官方」标识而非等级，仅作为发布主体。
     */
    AdminUserSummaryResponse updateOfficial(Long operatorId, Long userId, Boolean official, String officialTitle);

    List<WorkerCandidateResponse> workerCandidates(String keyword);

    List<AdminRoleResponse> listRoles();
}
