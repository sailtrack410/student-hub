package com.studenthub.modules.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 身份审核申请材料（V77）：只保存真实上传文件引用（file_storage.id）。
 *
 * <p>没有材料时本表无记录，管理端据此显示「未上传」，不构造占位图片。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_identity_evidence")
public class UserIdentityEvidenceEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private Long userId;
    /** file_storage.id，必填（仅登记真实存在的文件） */
    private Long fileId;
    /** STUDENT_CARD / ID_CARD / ENROLLMENT_PROOF / TEACHER_CERT / WORK_BADGE / OTHER */
    private String materialType;
    private String materialName;
    private String remark;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
}
