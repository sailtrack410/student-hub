package com.studenthub.modules.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_audit_log")
public class SysAuditLogEntity extends BaseEntity {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String action;
    private String resourceType;
    private String resourceId;
    private String resourceName;
    private String beforeJson;
    private String afterJson;
    /** SUCCESS / FAILED（V71）：失败写操作同样留痕 */
    private String resultStatus;
    /** 失败原因摘要（V71） */
    private String errorMessage;
    private String ip;
    private String userAgent;
    private String traceId;
}
