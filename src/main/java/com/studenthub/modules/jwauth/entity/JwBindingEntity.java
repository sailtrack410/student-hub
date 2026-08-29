package com.studenthub.modules.jwauth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jw_binding")
public class JwBindingEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    /** SSO 统一身份账号（即登录学号/工号） */
    private String ssoAccount;
    private String jwName;
    /** 教务系统内部学号（与学生 SSO 账号不同），教师为空 */
    private String jwStudentNo;
    /** 教务系统识别出的身份：STUDENT / TEACHER */
    private String identityKind;
    /** AUTHENTICATED / EXPIRED */
    private String authStatus;
    /** 年级（学生），如 2023 */
    private String grade;
    /** 院(系)/部（学生） */
    private String college;
    /** 专业（学生） */
    private String major;
    /** 班级（学生），如 计科B2316 */
    private String className;
    private LocalDateTime lastAuthAt;
    private LocalDateTime sessionExpiresAt;
    /** 最后一次同步课表的学期编码（学年,学期），如 2026,1；新学期没课时视图据此返回学期与空课表 */
    private String lastSemesterCode;
    /** 最后一次同步课表的学期名称，如 2026-2027学年第一学期 */
    private String lastSemesterName;
}
