package com.studenthub.modules.systemconfig.service;

import com.studenthub.modules.systemconfig.controller.dto.*;

import java.util.List;
import java.util.Map;

public interface SystemConfigService {
    List<AdminConfigItemResponse> list(String group, Long campusId);

    List<AdminConfigItemResponse> update(AdminConfigUpdateRequest request, Long operatorId, String operatorName);

    List<AdminFeatureFlagResponse> features(Long campusId);

    /** 设置功能开关灰度比例（0-100）。 */
    void updateRollout(String featureKey, Long campusId, int percent, Long operatorId);

    /** App 侧读取的有效功能开关（含校区覆盖）。 */
    Map<String, Boolean> effectiveFeatures(Long campusId);

    /** App 侧读取的有效功能开关：灰度比例按 userId 稳定分桶。 */
    Map<String, Boolean> effectiveFeatures(Long campusId, Long userId);

    AdminSystemHealthResponse health();

    List<AdminIntegrationStatusResponse> integrations();

    /** 对指定集成做真实配置/连通性探测；未配置或不可达如实返回，不伪造成功。 */
    AdminIntegrationTestResponse testIntegration(String code);

    String getValue(String key, Long campusId);

    boolean getBoolean(String key, Long campusId, boolean fallback);
}
