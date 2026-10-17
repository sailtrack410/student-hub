package com.studenthub.modules.screen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 现场终端信息要闻：NOTICE 通知 / NEWS 要闻 / ACTIVITY 活动，仅公开内容，无个人数据。
 */
@Data
@TableName("screen_notice")
public class ScreenNoticeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String noticeType;
    private String title;
    private String summary;
    private LocalDate publishDate;
    private Integer sortOrder;
    private Boolean enabled;
    private String channel;
    private String publishStatus;
    private LocalDateTime publishAt;
    private String author;
}
