package com.studenthub.modules.push.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 推送模板（V73）：同一校区内 template_code 唯一；标题/正文支持 {变量} 占位符。
 *
 * <p>渠道以逗号分隔存默认渠道集合，创建任务时可覆盖；模板本身不触发发送。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("push_template")
public class PushTemplateEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private String templateCode;
    private String templateName;
    /** 场景：SYSTEM / ACTIVITY / WORK_ORDER / ANNOUNCEMENT / AI_TODO ... */
    private String scene;
    private String titleTemplate;
    private String contentTemplate;
    /** JSON 数组字符串，记录模板声明的变量名 */
    private String variables;
    /** 逗号分隔默认渠道：LOG / WS / FCM / APNS / WEBPUSH */
    private String defaultChannels;
    /** ENABLED / DISABLED */
    private String status;
    private String remark;
}
