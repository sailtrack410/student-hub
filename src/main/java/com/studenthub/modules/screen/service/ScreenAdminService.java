package com.studenthub.modules.screen.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.screen.controller.dto.AdminNoticeResponse;
import com.studenthub.modules.screen.controller.dto.AppAnnouncementResponse;
import com.studenthub.modules.screen.controller.dto.NoticeUpsertRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 公告管理（渠道拆分 + 定时发布）：SCREEN 终端大屏 / APP 校园公告共用一张 screen_notice 表，
 * 以 channel 区分；publish_status 承载 草稿/定时/已发布/下线 生命周期，SCHEDULED 到点由定时器转 PUBLISHED。
 */
public interface ScreenAdminService {

    List<AdminNoticeResponse> list(String channel, String publishStatus, String noticeType);

    /** App 校园公告分页：仅 APP 渠道已发布公告，按发布日期倒序 */
    PageResult<AppAnnouncementResponse> pageAppAnnouncements(long pageNum, long pageSize);

    AdminNoticeResponse create(NoticeUpsertRequest request, Long operatorId);

    AdminNoticeResponse update(Long id, NoticeUpsertRequest request, Long operatorId);

    AdminNoticeResponse changeStatus(Long id, String publishStatus);

    void delete(Long id);

    /** 上传公告封面（PNG/JPEG/WebP，≤2MB，服务端内容嗅探），返回可公开访问的稳定地址 */
    String uploadCover(MultipartFile file);

    /** 定时发布扫描：把到点的 SCHEDULED 置为 PUBLISHED；返回本轮上线数 */
    int publishDueScheduled();
}
