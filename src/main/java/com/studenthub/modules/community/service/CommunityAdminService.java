package com.studenthub.modules.community.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.community.controller.dto.AdminCommunityAppealHandleRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityAppealResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityBannedWordRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityBannedWordResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityBatchAppealHandleRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityBatchPostStatusRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityBatchResult;
import com.studenthub.modules.community.controller.dto.AdminCommunityPostDetailResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityPostResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunitySectionReorderRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunitySectionRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunitySectionResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityStatsResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityTopicRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityTopicResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityTopicStatsResponse;
import com.studenthub.modules.community.controller.dto.AdminBannedWordImportResult;
import com.studenthub.modules.community.controller.dto.AdminModerationTemplateRequest;
import com.studenthub.modules.community.controller.dto.AdminModerationTemplateResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 管理端社区运营：社区数据概览、帖子管理、分区/话题、屏蔽词、申诉复核、处理模板。
 */
public interface CommunityAdminService {

    /**
     * 社区概览：days 仅支持 7/30；trend/活跃用户/分区/话题均按所选区间统计，
     * 举报/申诉/隐藏/屏蔽词为当前快照。
     */
    AdminCommunityStatsResponse stats(int days);

    /**
     * 概览 CSV 导出（UTF-8 + BOM）：时间范围、KPI、每日趋势、分区、话题 Top N 与口径说明。
     */
    byte[] overviewCsv(int days);

    PageResult<AdminCommunityPostResponse> pagePosts(int pageNum, int pageSize, String keyword,
                                                     Long sectionId, String status, String postType);

    AdminCommunityPostDetailResponse postDetail(Long postId);

    /** 帖子批量下架/恢复（C02），逐条校验并汇总跳过原因 */
    AdminCommunityBatchResult batchPostStatus(AdminCommunityBatchPostStatusRequest request);

    List<AdminCommunitySectionResponse> sections();

    AdminCommunitySectionResponse createSection(AdminCommunitySectionRequest request);

    AdminCommunitySectionResponse updateSection(Long id, AdminCommunitySectionRequest request);

    void deleteSection(Long id);

    /** 分区批量排序（C05）：按数组顺序写 sortOrder */
    AdminCommunityBatchResult reorderSections(AdminCommunitySectionReorderRequest request);

    List<AdminCommunityTopicResponse> topics();

    AdminCommunityTopicResponse createTopic(AdminCommunityTopicRequest request);

    AdminCommunityTopicResponse updateTopic(Long id, AdminCommunityTopicRequest request);

    void deleteTopic(Long id);

    /** 话题推荐位开关（C06） */
    AdminCommunityTopicResponse recommendTopic(Long id, boolean recommended);

    /** 话题合并（C06）：源话题的帖子标签改为目标话题，源话题停用并记录去向 */
    AdminCommunityTopicResponse mergeTopic(Long sourceTopicId, Long targetTopicId);

    /** 话题统计（C06）：关联帖子数 / 近 7 天新增 */
    AdminCommunityTopicStatsResponse topicStats(Long id);

    List<AdminCommunityBannedWordResponse> bannedWords(String keyword);

    AdminCommunityBannedWordResponse createBannedWord(AdminCommunityBannedWordRequest request);

    AdminCommunityBannedWordResponse updateBannedWord(Long id, AdminCommunityBannedWordRequest request);

    void deleteBannedWord(Long id);

    /** 导出屏蔽词为 Excel（列：屏蔽词/匹配方式/备注/状态/命中次数） */
    byte[] exportBannedWords();

    /** 从 Excel 导入屏蔽词（首行表头；重复词跳过） */
    AdminBannedWordImportResult importBannedWords(MultipartFile file);

    PageResult<AdminCommunityAppealResponse> pageAppeals(int pageNum, int pageSize, String status, String targetType);

    void handleAppeal(Long appealId, AdminCommunityAppealHandleRequest request);

    /** 申诉批量复核（C04）：逐条判定并通知，失败条目返回跳过原因 */
    AdminCommunityBatchResult batchHandleAppeals(AdminCommunityBatchAppealHandleRequest request);

    /** 处理模板列表（C03/C04）：平台内置 + 当前校区自定义 */
    List<AdminModerationTemplateResponse> templates(String templateType);

    AdminModerationTemplateResponse createTemplate(AdminModerationTemplateRequest request);

    AdminModerationTemplateResponse updateTemplate(Long id, AdminModerationTemplateRequest request);

    void deleteTemplate(Long id);
}
