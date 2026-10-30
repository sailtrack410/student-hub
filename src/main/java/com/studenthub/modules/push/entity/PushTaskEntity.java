package com.studenthub.modules.push.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 推送任务（V73）：人群 × 渠道 × 定时 × 频控的执行单元。
 *
 * <p>状态机：DRAFT → SCHEDULED → SENDING → SENT / PARTIAL / FAILED；任意非终态可 CANCELLED。
 * 计数列由发送/重试后按投递明细重算，不做假数据填充。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("push_task")
public class PushTaskEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private String taskName;
    private Long templateId;
    private String title;
    private String content;
    /** 模板变量取值（JSON 对象字符串），定时任务发送时据此渲染 */
    private String variables;
    /** ALL / ROLE / ORGANIZATION / USER */
    private String audienceType;
    /** ROLE/ORGANIZATION 存 ID；USER 存逗号分隔用户 ID；ALL 为空 */
    private String audienceValue;
    /** 逗号分隔渠道 */
    private String channels;
    /** NOW / SCHEDULED */
    private String scheduleType;
    private LocalDateTime scheduledAt;
    /** 同一用户在该窗口内最多接收多少次（跨任务累计），0 表示不限制 */
    private Integer frequencyLimit;
    private Integer frequencyWindowHours;
    /** DRAFT / SCHEDULED / SENDING / SENT / PARTIAL / FAILED / CANCELLED */
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer deliveredCount;
    private Integer clickedCount;
    private Integer skippedCount;
    private Integer notConfiguredCount;
    private Integer retryCount;
    private String lastError;
    /** 原子领取标识；仅持有者可提交执行结果。 */
    private String executionToken;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String remark;
}
