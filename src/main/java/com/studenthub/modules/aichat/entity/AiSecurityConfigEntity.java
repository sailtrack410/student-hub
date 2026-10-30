package com.studenthub.modules.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 安全策略配置（V73）：每个校区一行，campus_id=0 为平台级默认。
 *
 * <p>字段语义：限流/配额为 0 表示不限制；contentRetentionDays 仅在 retentionEnabled=true 时
 * 由 {@code AiContentRetentionJob} 执行清理（默认关闭，避免误删）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_security_config")
public class AiSecurityConfigEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    /** 安全策略总开关：关闭后敏感词与限流均不生效（保留配置）。 */
    private Boolean enabled;
    /** 敏感词，逗号或换行分隔。 */
    private String sensitiveWords;
    /** 工具白名单，逗号分隔（当前仅配置与展示，工具注册表尚未落地）。 */
    private String toolWhitelist;
    /** 每分钟每用户最大消息数，0 不限制。 */
    private Integer rateLimitPerMinute;
    /** 每用户每日最大消息数，0 不限制。 */
    private Integer dailyQuotaPerUser;
    /** 内容保留天数（仅在 retentionEnabled=true 时生效）。 */
    private Integer contentRetentionDays;
    /** 是否启用历史内容自动清理（默认关闭）。 */
    private Boolean retentionEnabled;
    /** 命中敏感词时是否直接拦截（否则仅记录）。 */
    private Boolean blockOnHit;
    private String remark;
}
