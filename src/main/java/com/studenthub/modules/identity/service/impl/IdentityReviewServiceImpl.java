package com.studenthub.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.privacy.PhoneMasker;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.file.entity.FileStorageEntity;
import com.studenthub.modules.file.mapper.FileStorageMapper;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.controller.dto.AdminIdentityReviewResponse;
import com.studenthub.modules.identity.controller.dto.IdentityEvidenceRequest;
import com.studenthub.modules.identity.controller.dto.IdentityEvidenceResponse;
import com.studenthub.modules.identity.controller.dto.IdentityRejectReasonRequest;
import com.studenthub.modules.identity.controller.dto.IdentityRejectReasonResponse;
import com.studenthub.modules.identity.controller.dto.IdentityReviewTimelineEntry;
import com.studenthub.modules.identity.entity.IdentityRejectReasonEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.entity.UserIdentityEvidenceEntity;
import com.studenthub.modules.identity.entity.UserIdentityReviewEntity;
import com.studenthub.modules.identity.mapper.IdentityRejectReasonMapper;
import com.studenthub.modules.identity.mapper.UserIdentityEvidenceMapper;
import com.studenthub.modules.identity.mapper.UserIdentityReviewMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.identity.service.IdentityReviewService;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 身份审核增强实现。
 *
 * <p>关键约束：材料只能引用真实文件（fileId 校验存在），文件不可读时返回 previewAvailable=false，
 * 由前端显示「文件缺失」；拒绝原因取自数据库模板，拒绝时模板/自定义原因必须至少提供一项；
 * 审核结果通过 {@link NotificationService} 投递站内通知（真实落库，App 消息中心可读）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityReviewServiceImpl implements IdentityReviewService {

    private static final long PLATFORM_CAMPUS_ID = 0L;
    private static final Set<String> MATERIAL_TYPES = Set.of(
            "STUDENT_CARD", "ID_CARD", "ENROLLMENT_PROOF", "TEACHER_CERT", "WORK_BADGE", "OTHER");
    private static final Set<String> TEMPLATE_STATUSES = Set.of("ENABLED", "DISABLED");
    private static final int MAX_TIMELINE = 20;
    private static final int MAX_SNAPSHOT_LENGTH = 900;
    private static final int MAX_NOTIFICATION_CONTENT = 500;

    private static final Map<String, String> MATERIAL_LABELS = Map.of(
            "STUDENT_CARD", "学生证",
            "ID_CARD", "身份证",
            "ENROLLMENT_PROOF", "在读证明",
            "TEACHER_CERT", "教师资格证",
            "WORK_BADGE", "工作证/在职证明",
            "OTHER", "其他材料");

    private final UserMapper userMapper;
    private final UserIdentityEvidenceMapper evidenceMapper;
    private final UserIdentityReviewMapper reviewMapper;
    private final IdentityRejectReasonMapper reasonMapper;
    private final FileStorageMapper fileStorageMapper;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;
    private final AdminCampusContextService campusContextService;

    @Override
    public AdminIdentityReviewResponse detail(Long userId) {
        UserEntity user = requireCampusUser(userId);
        List<UserIdentityEvidenceEntity> evidences = evidenceMapper.selectList(
                new LambdaQueryWrapper<UserIdentityEvidenceEntity>()
                        .eq(UserIdentityEvidenceEntity::getUserId, user.getId())
                        .orderByDesc(UserIdentityEvidenceEntity::getUploadedAt)
                        .orderByDesc(UserIdentityEvidenceEntity::getId));
        List<IdentityEvidenceResponse> materials = toMaterialResponses(evidences);
        List<UserIdentityReviewEntity> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<UserIdentityReviewEntity>()
                        .eq(UserIdentityReviewEntity::getUserId, user.getId())
                        .orderByDesc(UserIdentityReviewEntity::getReviewedAt)
                        .orderByDesc(UserIdentityReviewEntity::getId)
                        .last("LIMIT " + MAX_TIMELINE));
        List<IdentityReviewTimelineEntry> timeline = reviews.stream()
                .map(IdentityReviewTimelineEntry::from).toList();
        String reviewerName = null;
        if (user.getAuthorizationReviewedBy() != null) {
            UserEntity reviewer = userMapper.selectById(user.getAuthorizationReviewedBy());
            reviewerName = reviewer == null ? null : reviewer.getDisplayName();
        }
        return new AdminIdentityReviewResponse(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getStudentNo(),
                user.getCollege(), PhoneMasker.mask(user.getPhone()), user.getIdentityType(),
                user.getAuthorizationStatus(), user.getAuthorizationNote(), user.getAuthorizationSubmittedAt(),
                user.getAuthorizationReviewedBy(), reviewerName, user.getAuthorizationReviewedAt(),
                materials, timeline, listRejectTemplates("ENABLED"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminIdentityReviewResponse addEvidence(Long operatorId, Long userId, IdentityEvidenceRequest request) {
        UserEntity user = requireCampusUser(userId);
        String materialType = normalizeEnum(request.materialType());
        if (materialType == null || !MATERIAL_TYPES.contains(materialType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "材料类型仅支持 " + String.join("/", MATERIAL_TYPES.stream().sorted().toList()));
        }
        FileStorageEntity file = fileStorageMapper.selectById(request.fileId());
        if (file == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文件不存在，请先上传后再登记材料");
        }
        Long duplicate = evidenceMapper.selectCount(new LambdaQueryWrapper<UserIdentityEvidenceEntity>()
                .eq(UserIdentityEvidenceEntity::getUserId, user.getId())
                .eq(UserIdentityEvidenceEntity::getFileId, file.getId()));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该文件已登记为审核材料");
        }
        UserIdentityEvidenceEntity entity = new UserIdentityEvidenceEntity();
        entity.setCampusId(user.getCampusId());
        entity.setUserId(user.getId());
        entity.setFileId(file.getId());
        entity.setMaterialType(materialType);
        String preferredName = StringUtils.hasText(request.materialName())
                ? request.materialName().trim()
                : (file.getOriginalName() == null ? MATERIAL_LABELS.get(materialType) : file.getOriginalName());
        entity.setMaterialName(truncate(preferredName, 128));
        entity.setRemark(request.remark());
        entity.setUploadedBy(operatorId);
        entity.setUploadedAt(LocalDateTime.now());
        evidenceMapper.insert(entity);
        log.info("身份审核材料已登记, userId={}, evidenceId={}, fileId={}, operatorId={}",
                user.getId(), entity.getId(), file.getId(), operatorId);
        return detail(user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminIdentityReviewResponse removeEvidence(Long operatorId, Long userId, Long evidenceId) {
        UserEntity user = requireCampusUser(userId);
        UserIdentityEvidenceEntity evidence = evidenceId == null ? null : evidenceMapper.selectById(evidenceId);
        if (evidence == null || !evidence.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "审核材料不存在");
        }
        evidenceMapper.deleteById(evidence.getId());
        log.info("身份审核材料已删除, userId={}, evidenceId={}, operatorId={}", user.getId(), evidenceId, operatorId);
        return detail(user.getId());
    }

    @Override
    public List<IdentityRejectReasonResponse> listRejectTemplates(String status) {
        String normalizedStatus = normalizeEnum(status);
        List<Long> campusIds = new ArrayList<>();
        campusIds.add(PLATFORM_CAMPUS_ID);
        Long currentCampusId = campusContextService.currentCampusId();
        if (!campusIds.contains(currentCampusId)) {
            campusIds.add(currentCampusId);
        }
        List<IdentityRejectReasonEntity> rows = reasonMapper.selectList(
                new LambdaQueryWrapper<IdentityRejectReasonEntity>()
                        .in(IdentityRejectReasonEntity::getCampusId, campusIds)
                        .eq(normalizedStatus != null, IdentityRejectReasonEntity::getStatus, normalizedStatus)
                        // 校区自定义优先（campus_id 大的在前），同编码时覆盖平台内置，避免下拉出现重复项
                        .orderByDesc(IdentityRejectReasonEntity::getCampusId)
                        .orderByAsc(IdentityRejectReasonEntity::getSortOrder)
                        .orderByAsc(IdentityRejectReasonEntity::getId));
        Map<String, IdentityRejectReasonEntity> merged = new java.util.LinkedHashMap<>();
        for (IdentityRejectReasonEntity row : rows) {
            merged.putIfAbsent(row.getTemplateCode(), row);
        }
        List<IdentityRejectReasonEntity> ordered = new ArrayList<>(merged.values());
        ordered.sort(java.util.Comparator
                .comparing((IdentityRejectReasonEntity item) -> item.getSortOrder() == null ? 0 : item.getSortOrder())
                .thenComparing(IdentityRejectReasonEntity::getId));
        return ordered.stream().map(IdentityRejectReasonResponse::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IdentityRejectReasonResponse createRejectTemplate(Long operatorId, IdentityRejectReasonRequest request) {
        Long campusId = campusContextService.currentCampusId();
        String code = request.templateCode().trim().toUpperCase(Locale.ROOT);
        Long exists = reasonMapper.selectCount(new LambdaQueryWrapper<IdentityRejectReasonEntity>()
                .eq(IdentityRejectReasonEntity::getCampusId, campusId)
                .eq(IdentityRejectReasonEntity::getTemplateCode, code));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板编码已存在：" + code);
        }
        IdentityRejectReasonEntity entity = new IdentityRejectReasonEntity();
        entity.setCampusId(campusId);
        entity.setTemplateCode(code);
        entity.setBuiltin(false);
        applyTemplate(entity, request);
        reasonMapper.insert(entity);
        log.info("拒绝原因模板已创建, campusId={}, code={}, operatorId={}", campusId, code, operatorId);
        return IdentityRejectReasonResponse.from(reasonMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IdentityRejectReasonResponse updateRejectTemplate(Long operatorId, Long templateId,
                                                             IdentityRejectReasonRequest request) {
        IdentityRejectReasonEntity entity = requireCustomTemplate(templateId);
        String code = request.templateCode() == null ? null : request.templateCode().trim().toUpperCase(Locale.ROOT);
        if (StringUtils.hasText(code) && !code.equals(entity.getTemplateCode())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板编码创建后不可修改");
        }
        applyTemplate(entity, request);
        reasonMapper.updateById(entity);
        log.info("拒绝原因模板已更新, id={}, operatorId={}", templateId, operatorId);
        return IdentityRejectReasonResponse.from(reasonMapper.selectById(templateId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRejectTemplate(Long operatorId, Long templateId) {
        IdentityRejectReasonEntity entity = requireCustomTemplate(templateId);
        reasonMapper.deleteById(entity.getId());
        log.info("拒绝原因模板已删除, id={}, operatorId={}", templateId, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordDecision(Long operatorId, UserEntity user, String fromStatus, String toStatus,
                               String reasonCode, String reason) {
        UserIdentityReviewEntity review = new UserIdentityReviewEntity();
        review.setCampusId(user.getCampusId());
        review.setUserId(user.getId());
        review.setAction("AUTHORIZED".equals(toStatus) ? "APPROVE" : "REJECT");
        review.setFromStatus(fromStatus);
        review.setToStatus(toStatus);
        review.setReasonCode(reasonCode);
        review.setReason(truncate(reason, 500));
        review.setEvidenceSnapshot(buildEvidenceSnapshot(user.getId()));
        review.setReviewerId(operatorId);
        review.setReviewerName(currentUserProvider.currentUser()
                .map(operator -> StringUtils.hasText(operator.displayName()) ? operator.displayName() : "管理员")
                .orElse("平台管理员"));
        review.setReviewedAt(LocalDateTime.now());
        reviewMapper.insert(review);

        boolean approved = "APPROVE".equals(review.getAction());
        String title = approved ? "身份授权已通过" : "身份授权未通过";
        String content = approved
                ? "你的身份授权申请已通过审核，对应业务功能已开通。"
                : "你的身份授权申请未通过：" + (StringUtils.hasText(reason) ? reason : "请补充材料后重新提交")
                + "。可在「我的」中查看并重新申请。";
        try {
            notificationService.send(new NotificationSendCommand(
                    user.getId(), "IDENTITY_REVIEW", title, truncate(content, MAX_NOTIFICATION_CONTENT),
                    "IDENTITY_REVIEW", review.getId()));
        } catch (RuntimeException exception) {
            // 通知属于结果告知，失败不能回滚审核结果；记录后用日志与独立审核记录兜底追踪。
            log.warn("身份审核结果通知发送失败, userId={}, reviewId={}", user.getId(), review.getId(), exception);
        }
        log.info("身份审核记录已写入, userId={}, action={}, from={}, to={}, reviewerId={}",
                user.getId(), review.getAction(), fromStatus, toStatus, operatorId);
    }

    @Override
    public String resolveReason(String action, String reasonCode, String reason) {
        String custom = StringUtils.hasText(reason) ? reason.trim() : null;
        boolean approve = "APPROVE".equalsIgnoreCase(action);
        if (approve) {
            return custom == null ? "审核通过" : "审核通过：" + custom;
        }
        String templateText = null;
        if (StringUtils.hasText(reasonCode)) {
            IdentityRejectReasonEntity template = reasonMapper.selectOne(
                    new LambdaQueryWrapper<IdentityRejectReasonEntity>()
                            .eq(IdentityRejectReasonEntity::getTemplateCode, reasonCode.trim().toUpperCase(Locale.ROOT))
                            .eq(IdentityRejectReasonEntity::getStatus, "ENABLED")
                            .in(IdentityRejectReasonEntity::getCampusId, campusIdsForRead())
                            .orderByDesc(IdentityRejectReasonEntity::getCampusId)
                            .last("LIMIT 1"));
            if (template == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "拒绝原因模板不存在或已停用：" + reasonCode);
            }
            templateText = template.getTemplateText();
        }
        if (templateText == null && custom == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "拒绝时必须选择原因模板或填写自定义原因");
        }
        if (templateText == null) {
            return truncate(custom, 500);
        }
        if (custom == null) {
            return truncate(templateText, 500);
        }
        if ("OTHER".equalsIgnoreCase(reasonCode)) {
            // 「其他原因」以自定义说明为主，模板文案只作占位
            return truncate(custom, 500);
        }
        return truncate(templateText + "；补充说明：" + custom, 500);
    }

    // ------------------------------------------------------------------ 内部

    private void applyTemplate(IdentityRejectReasonEntity entity, IdentityRejectReasonRequest request) {
        String status = normalizeEnum(request.status());
        if (status != null && !TEMPLATE_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板状态仅支持 ENABLED/DISABLED");
        }
        entity.setTemplateText(request.templateText().trim());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setStatus(status == null ? "ENABLED" : status);
        entity.setRemark(request.remark());
    }

    private IdentityRejectReasonEntity requireCustomTemplate(Long templateId) {
        IdentityRejectReasonEntity entity = templateId == null ? null : reasonMapper.selectById(templateId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "拒绝原因模板不存在");
        }
        if (Boolean.TRUE.equals(entity.getBuiltin()) || Long.valueOf(PLATFORM_CAMPUS_ID).equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID,
                    "平台内置模板不可修改/删除，如需调整请新建本校区自定义模板");
        }
        if (!campusContextService.currentCampusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "拒绝原因模板不存在");
        }
        return entity;
    }

    private List<Long> campusIdsForRead() {
        List<Long> campusIds = new ArrayList<>();
        campusIds.add(PLATFORM_CAMPUS_ID);
        Long current = campusContextService.currentCampusId();
        if (!campusIds.contains(current)) {
            campusIds.add(current);
        }
        return campusIds;
    }

    private List<IdentityEvidenceResponse> toMaterialResponses(List<UserIdentityEvidenceEntity> evidences) {
        if (evidences.isEmpty()) {
            return List.of();
        }
        List<Long> fileIds = evidences.stream().map(UserIdentityEvidenceEntity::getFileId).distinct().toList();
        Map<Long, FileStorageEntity> files = fileStorageMapper.selectBatchIds(fileIds).stream()
                .collect(Collectors.toMap(FileStorageEntity::getId, Function.identity(), (a, b) -> a));
        List<IdentityEvidenceResponse> responses = new ArrayList<>();
        for (UserIdentityEvidenceEntity evidence : evidences) {
            FileStorageEntity file = files.get(evidence.getFileId());
            boolean available = file != null;
            String viewPath = null;
            String thumbnailPath = null;
            boolean image = file != null && file.getContentType() != null
                    && file.getContentType().toLowerCase(Locale.ROOT).startsWith("image/");
            if (available) {
                try {
                    viewPath = fileStorageService.signViewPath(file.getId());
                    if (image) {
                        thumbnailPath = fileStorageService.signThumbnailPath(file.getId());
                    }
                } catch (RuntimeException exception) {
                    available = false;
                    log.warn("身份审核材料签名失败, fileId={}", file.getId(), exception);
                }
            }
            responses.add(new IdentityEvidenceResponse(
                    evidence.getId(), evidence.getFileId(), evidence.getMaterialType(),
                    evidence.getMaterialName(), evidence.getRemark(),
                    file == null ? null : file.getOriginalName(),
                    file == null ? null : file.getContentType(),
                    file == null ? null : file.getFileSize(),
                    image, viewPath, thumbnailPath, available,
                    evidence.getUploadedBy(), evidence.getUploadedAt()));
        }
        return responses;
    }

    private String buildEvidenceSnapshot(Long userId) {
        List<UserIdentityEvidenceEntity> evidences = evidenceMapper.selectList(
                new LambdaQueryWrapper<UserIdentityEvidenceEntity>()
                        .eq(UserIdentityEvidenceEntity::getUserId, userId)
                        .orderByAsc(UserIdentityEvidenceEntity::getId)
                        .last("LIMIT 20"));
        if (evidences.isEmpty()) {
            return null;
        }
        String snapshot = evidences.stream()
                .map(item -> item.getId() + ":" + (item.getMaterialName() == null
                        ? MATERIAL_LABELS.getOrDefault(item.getMaterialType(), item.getMaterialType())
                        : item.getMaterialName()))
                .collect(Collectors.joining(","));
        return truncate(snapshot, MAX_SNAPSHOT_LENGTH);
    }

    private UserEntity requireCampusUser(Long userId) {
        UserEntity user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        Long campusId = campusContextService.currentCampusId();
        if (user.getCampusId() != null && !user.getCampusId().equals(campusId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在或不属于当前校区");
        }
        return user;
    }

    private String normalizeEnum(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }
}
