package com.studenthub.modules.identity.service;

import com.studenthub.modules.identity.controller.dto.AdminOrganizationLeaderCandidateResponse;
import com.studenthub.modules.identity.controller.dto.AdminOrganizationRequest;
import com.studenthub.modules.identity.controller.dto.AdminOrganizationResponse;

import java.util.List;

public interface AdminOrganizationService {

    /**
     * 组织列表（扁平，按 sortOrder/id 排序；附带 level、leaderName、childCount、userCount，
     * 前端据此渲染树形缩进与删除保护提示）。
     */
    List<AdminOrganizationResponse> list();

    AdminOrganizationResponse create(Long operatorId, AdminOrganizationRequest request);

    AdminOrganizationResponse update(Long operatorId, Long organizationId, AdminOrganizationRequest request);

    /**
     * 组织负责人候选（当前校区启用用户，轻量字段）。
     */
    List<AdminOrganizationLeaderCandidateResponse> leaderCandidates();

    /**
     * 删除组织（U03）：存在直接子组织或仍有用户（默认组织/角色绑定）时拒绝；删除为软删。
     */
    void delete(Long operatorId, Long organizationId);

    /**
     * 同级排序（U03）：在当前父组织下与相邻兄弟交换位置，并把整组 sortOrder 重排为 10/20/30…。
     *
     * @param direction UP / DOWN
     */
    void move(Long operatorId, Long organizationId, String direction);
}
