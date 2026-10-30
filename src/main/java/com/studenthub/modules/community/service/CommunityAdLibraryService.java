package com.studenthub.modules.community.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.community.controller.dto.AdminAdMaterialPageQuery;
import com.studenthub.modules.community.controller.dto.AdminAdMaterialRequest;
import com.studenthub.modules.community.controller.dto.AdminAdMaterialResponse;
import com.studenthub.modules.community.controller.dto.AdminAdMaterialReviewRequest;
import com.studenthub.modules.community.controller.dto.AdminAdPlanPageQuery;
import com.studenthub.modules.community.controller.dto.AdminAdPlanRequest;
import com.studenthub.modules.community.controller.dto.AdminAdPlanResponse;

/**
 * 广告投放计划与素材库（C13）：计划的 CRUD/启停/分页，素材的 CRUD/审核/启停/分页。
 * 直投广告可最小侵入关联计划/素材（nullable）；素材必须审核通过才允许被广告引用。
 */
public interface CommunityAdLibraryService {

    PageResult<AdminAdPlanResponse> pagePlans(AdminAdPlanPageQuery query);

    AdminAdPlanResponse createPlan(AdminAdPlanRequest request);

    AdminAdPlanResponse updatePlan(Long planId, AdminAdPlanRequest request);

    AdminAdPlanResponse updatePlanStatus(Long planId, String status);

    void deletePlan(Long planId);

    PageResult<AdminAdMaterialResponse> pageMaterials(AdminAdMaterialPageQuery query);

    AdminAdMaterialResponse createMaterial(AdminAdMaterialRequest request);

    AdminAdMaterialResponse updateMaterial(Long materialId, AdminAdMaterialRequest request);

    AdminAdMaterialResponse reviewMaterial(Long materialId, AdminAdMaterialReviewRequest request);

    AdminAdMaterialResponse updateMaterialStatus(Long materialId, String status);

    void deleteMaterial(Long materialId);
}
