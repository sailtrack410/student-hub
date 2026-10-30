package com.studenthub.modules.identity.service;

import com.studenthub.modules.identity.controller.dto.AdminIdentityReviewResponse;
import com.studenthub.modules.identity.controller.dto.IdentityEvidenceRequest;
import com.studenthub.modules.identity.controller.dto.IdentityRejectReasonRequest;
import com.studenthub.modules.identity.controller.dto.IdentityRejectReasonResponse;
import com.studenthub.modules.identity.entity.UserEntity;

import java.util.List;

/**
 * 身份审核增强（U04）：申请材料、审核历史、拒绝原因模板，以及审核结果的站内通知。
 *
 * <p>材料只登记真实上传文件（file_storage），无材料时返回空列表，前端显示「未上传」；
 * 拒绝原因从数据库模板读取（平台内置 + 本校区自定义），不写死在前端。</p>
 */
public interface IdentityReviewService {

    /** 审核详情：申请人 + 材料 + 审核历史 + 可用拒绝原因模板。 */
    AdminIdentityReviewResponse detail(Long userId);

    /** 登记申请材料（fileId 必须是已上传的真实文件）。 */
    AdminIdentityReviewResponse addEvidence(Long operatorId, Long userId, IdentityEvidenceRequest request);

    /** 删除申请材料（逻辑删除，审核历史中的材料快照保留）。 */
    AdminIdentityReviewResponse removeEvidence(Long operatorId, Long userId, Long evidenceId);

    /** 可用拒绝原因模板：平台内置（campus_id=0）+ 当前校区自定义。 */
    List<IdentityRejectReasonResponse> listRejectTemplates(String status);

    IdentityRejectReasonResponse createRejectTemplate(Long operatorId, IdentityRejectReasonRequest request);

    IdentityRejectReasonResponse updateRejectTemplate(Long operatorId, Long templateId,
                                                      IdentityRejectReasonRequest request);

    void deleteRejectTemplate(Long operatorId, Long templateId);

    /**
     * 写入独立审核记录并给申请人发送站内通知；由授权审核流程在同一事务内调用。
     *
     * @param operatorId 审核人
     * @param user       被审核用户（审核前快照，用于取 campusId/username）
     * @param fromStatus 审核前状态
     * @param toStatus   审核后状态
     * @param reasonCode 拒绝原因模板编码（可空）
     * @param reason     最终备注（模板文案与自定义原因拼接后的结果）
     */
    void recordDecision(Long operatorId, UserEntity user, String fromStatus, String toStatus,
                        String reasonCode, String reason);

    /**
     * 解析最终审核备注：通过→自定义备注或默认文案；拒绝→模板文案（缺失则 400）+ 可选自定义原因补充。
     */
    String resolveReason(String action, String reasonCode, String reason);
}
