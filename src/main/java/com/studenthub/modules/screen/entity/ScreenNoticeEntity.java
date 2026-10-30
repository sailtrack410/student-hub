package com.studenthub.modules.screen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 现场终端信息要闻：NOTICE 通知 / NEWS 要闻 / ACTIVITY 活动，仅公开内容，无个人数据。
 * V71 增加正文（content）与封面（cover_image_url），供管理端编辑与会场/App 展示。
 */
@Data
@TableName("screen_notice")
public class ScreenNoticeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String noticeType;
    private String title;
    private String summary;
    /** 正文（纯文本，保留换行；v71） */
    private String content;
    /** 封面图地址（/api/common/assets/notices/xxx.png；v71） */
    private String coverImageUrl;
    private LocalDate publishDate;
    private Integer sortOrder;
    private Boolean enabled;
    private String channel;
    private String publishStatus;
    private LocalDateTime publishAt;
    private String author;
}
