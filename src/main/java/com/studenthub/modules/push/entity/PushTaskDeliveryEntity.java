package com.studenthub.modules.push.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 推送投递明细（V73）：任务 × 用户 × 渠道 唯一。
 *
 * <p>状态语义（避免伪造成到达）：
 * SENT = 已交给通道但无到达证明；DELIVERED = 通道确认到达（在线 WebSocket 写入成功）；
 * CLICKED = 用户点击（由 App 回调记录）；FAILED = 通道明确失败；
 * NOT_CONFIGURED = 通道未配置（不计成功也不计失败）；SKIPPED = 频控/无效接收人被跳过。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("push_task_delivery")
public class PushTaskDeliveryEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;
    private Long campusId;
    private Long userId;
    private String channel;
    private String status;
    /** 通道侧回执摘要（不含敏感信息） */
    private String providerMessage;
    private String errorCode;
    private String errorMessage;
    private Integer attempts;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime clickedAt;
}
