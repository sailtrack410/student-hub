package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广告事件（V66）：一条事件一行，click_id 串起曝光→点击→落地→停留/跳出链路。
 * 写入型事件表只追加不改写，因此不继承 BaseEntity（无逻辑删除与乐观锁字段）。
 */
@Data
@TableName("community_ad_event")
public class CommunityAdEventEntity {

    public static final String TYPE_IMPRESSION = "IMPRESSION";
    public static final String TYPE_CLICK = "CLICK";
    public static final String TYPE_LANDING = "LANDING";
    public static final String TYPE_DWELL = "DWELL";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long adId;
    private String eventType;
    private Long userId;
    private String sessionId;
    private String clickId;
    /** 停留毫秒，仅 DWELL 事件有值 */
    private Long dwellMs;
    /** 是否在广告配置的 N 秒内跳出，仅 DWELL 事件有值；统计接口按参数 N 重算 */
    private Boolean bounced;
    private String landingType;
    private LocalDateTime createdAt;
}
