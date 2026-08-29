package com.studenthub.modules.schedule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studenthub.schedule-sync")
public class ScheduleSyncProperties {

    /** 是否启用定时同步 */
    private boolean enabled = true;

    /** Spring cron 表达式：默认每天 7/9/11/13/15/17/19/21 点整触发 */
    private String cron = "0 0 7-21/2 * * *";

    /** 单轮同步时，班级之间的最大随机错峰秒数（0 = 不错峰） */
    private int staggerMaxSeconds = 30;
}