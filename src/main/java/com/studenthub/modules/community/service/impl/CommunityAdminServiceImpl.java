package com.studenthub.modules.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.common.util.JsonIds;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.controller.dto.AdminCommunityAppealHandleRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityAppealResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityBannedWordRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityBannedWordResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityPostDetailResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityPostResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunitySectionRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunitySectionResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityStatsResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityTopicRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityTopicResponse;
import com.studenthub.modules.community.controller.dto.AdminBannedWordImportResult;
import com.studenthub.modules.community.controller.dto.AdminCommunityBatchAppealHandleRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityBatchPostStatusRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityBatchResult;
import com.studenthub.modules.community.controller.dto.AdminCommunitySectionReorderRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityTopicStatsResponse;
import com.studenthub.modules.community.controller.dto.AdminModerationTemplateRequest;
import com.studenthub.modules.community.controller.dto.AdminModerationTemplateResponse;
import com.studenthub.modules.audit.aspect.AuditTrailContext;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;
import com.studenthub.modules.community.controller.dto.CommunityContentBlockResponse;
import com.studenthub.modules.community.entity.CommunityAppealEntity;
import com.studenthub.modules.community.entity.CommunityBannedWordEntity;
import com.studenthub.modules.community.entity.CommunityCommentEntity;
import com.studenthub.modules.community.entity.CommunityModerationTemplateEntity;
import com.studenthub.modules.community.entity.CommunityPostEntity;
import com.studenthub.modules.community.entity.CommunitySectionEntity;
import com.studenthub.modules.community.entity.CommunityTopicEntity;
import com.studenthub.modules.community.entity.CommunityUserSanctionEntity;
import com.studenthub.modules.community.mapper.CommunityAppealMapper;
import com.studenthub.modules.community.mapper.CommunityBannedWordMapper;
import com.studenthub.modules.community.mapper.CommunityCommentMapper;
import com.studenthub.modules.community.mapper.CommunityModerationTemplateMapper;
import com.studenthub.modules.community.mapper.CommunityPostMapper;
import com.studenthub.modules.community.mapper.CommunityReportMapper;
import com.studenthub.modules.community.mapper.CommunitySectionMapper;
import com.studenthub.modules.community.mapper.CommunityTopicMapper;
import com.studenthub.modules.community.mapper.CommunityUserSanctionMapper;
import com.studenthub.modules.community.service.BannedWordService;
import com.studenthub.modules.community.service.CommunityAdminService;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端社区运营实现：概览统计、帖子管理、分区/话题、屏蔽词、申诉复核。
 * 全部按当前管理员的 campusId 隔离；申诉通过按对象类型恢复内容/撤销处罚并通知用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityAdminServiceImpl implements CommunityAdminService {

    private static final String ENABLED = "ENABLED";
    private static final String HIDDEN = "HIDDEN";
    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String TARGET_POST = "POST";
    private static final String TARGET_COMMENT = "COMMENT";
    private static final String TARGET_SANCTION = "SANCTION";
    private static final Set<String> TEMPLATE_TYPES = Set.of("REPORT_NOTE", "APPEAL_REPLY");
    private static final Set<String> TEMPLATE_REASON_CODES = Set.of("AD", "ABUSE", "PORN", "POLITICS",
            "PRIVACY", "OTHER");
    /** 概览支持的时间范围（天）：仅 7/30，避免任意区间拖垮统计 */
    private static final Set<Integer> SUPPORTED_DAYS = Set.of(7, 30);
    /** 概览话题维度 Top N */
    private static final int TOP_TOPIC_LIMIT = 10;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final CommunityReportMapper reportMapper;
    private final CommunitySectionMapper sectionMapper;
    private final CommunityTopicMapper topicMapper;
    private final CommunityBannedWordMapper bannedWordMapper;
    private final CommunityAppealMapper appealMapper;
    private final CommunityUserSanctionMapper sanctionMapper;
    private final CommunityModerationTemplateMapper templateMapper;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;
    private final BannedWordService bannedWordService;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;
    private final AdminCampusContextService campusContextService;
    private final ObjectMapper objectMapper;

    @Override
    public AdminCommunityStatsResponse stats(int days) {
        int rangeDays = normalizeDays(days);
        Long campusId = campusId();
        LocalDate todayDate = LocalDate.now(ZONE);
        LocalDateTime today = todayDate.atStartOfDay();
        LocalDateTime since = today.minusDays(rangeDays - 1L);

        long totalPosts = countPosts(campusId, ENABLED, null);
        long todayPosts = postMapper.selectCount(new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getCampusId, campusId)
                .eq(CommunityPostEntity::getStatus, ENABLED)
                .ge(CommunityPostEntity::getCreatedAt, today));
        long totalComments = commentMapper.selectCount(new LambdaQueryWrapper<CommunityCommentEntity>()
                .eq(CommunityCommentEntity::getStatus, ENABLED)
                .inSql(CommunityCommentEntity::getPostId,
                        "SELECT id FROM community_post WHERE campus_id = " + campusId + " AND deleted = 0"));
        long todayComments = commentMapper.selectCount(new LambdaQueryWrapper<CommunityCommentEntity>()
                .eq(CommunityCommentEntity::getStatus, ENABLED)
                .ge(CommunityCommentEntity::getCreatedAt, today)
                .inSql(CommunityCommentEntity::getPostId,
                        "SELECT id FROM community_post WHERE campus_id = " + campusId + " AND deleted = 0"));
        long hiddenPosts = countPosts(campusId, HIDDEN, null);
        long hiddenComments = commentMapper.selectCount(new LambdaQueryWrapper<CommunityCommentEntity>()
                .eq(CommunityCommentEntity::getStatus, HIDDEN)
                .inSql(CommunityCommentEntity::getPostId,
                        "SELECT id FROM community_post WHERE campus_id = " + campusId + " AND deleted = 0"));
        long pendingReports = reportMapper.selectCount(new LambdaQueryWrapper<com.studenthub.modules.community.entity.CommunityReportEntity>()
                .eq(com.studenthub.modules.community.entity.CommunityReportEntity::getCampusId, campusId)
                .eq(com.studenthub.modules.community.entity.CommunityReportEntity::getStatus, PENDING));
        long pendingAppeals = appealMapper.selectCount(new LambdaQueryWrapper<CommunityAppealEntity>()
                .eq(CommunityAppealEntity::getCampusId, campusId)
                .eq(CommunityAppealEntity::getStatus, PENDING));
        long bannedWordCount = bannedWordMapper.selectCount(new LambdaQueryWrapper<CommunityBannedWordEntity>()
                .eq(CommunityBannedWordEntity::getCampusId, campusId)
                .eq(CommunityBannedWordEntity::getStatus, ENABLED));

        List<AdminCommunityStatsResponse.DayCount> trend = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        long rangePosts = 0;
        for (int i = rangeDays - 1; i >= 0; i--) {
            LocalDateTime start = todayDate.minusDays(i).atStartOfDay();
            LocalDateTime end = start.plusDays(1);
            long count = postMapper.selectCount(new LambdaQueryWrapper<CommunityPostEntity>()
                    .eq(CommunityPostEntity::getCampusId, campusId)
                    .eq(CommunityPostEntity::getStatus, ENABLED)
                    .ge(CommunityPostEntity::getCreatedAt, start)
                    .lt(CommunityPostEntity::getCreatedAt, end));
            rangePosts += count;
            trend.add(new AdminCommunityStatsResponse.DayCount(start.format(formatter), count));
        }

        Set<Long> activeUsers = new HashSet<>();
        postMapper.selectObjs(new QueryWrapper<CommunityPostEntity>()
                        .select("DISTINCT author_id")
                        .eq("campus_id", campusId)
                        .eq("status", ENABLED)
                        .ge("created_at", since))
                .forEach(value -> addId(activeUsers, value));
        commentMapper.selectObjs(new QueryWrapper<CommunityCommentEntity>()
                        .select("DISTINCT author_id")
                        .eq("status", ENABLED)
                        .ge("created_at", since)
                        .inSql("post_id", "SELECT id FROM community_post WHERE campus_id = " + campusId + " AND deleted = 0"))
                .forEach(value -> addId(activeUsers, value));

        List<AdminCommunityStatsResponse.SectionCount> sectionCounts = new ArrayList<>();
        for (CommunitySectionEntity section : sectionsOf(campusId)) {
            sectionCounts.add(new AdminCommunityStatsResponse.SectionCount(
                    section.getId(), section.getSectionName(), countRangePosts(campusId, section.getId(), since)));
        }

        List<AdminCommunityStatsResponse.TopicCount> topics = topicCounts(campusId, since, TOP_TOPIC_LIMIT);

        return new AdminCommunityStatsResponse(totalPosts, todayPosts, totalComments, todayComments,
                activeUsers.size(), pendingReports, hiddenPosts, hiddenComments, pendingAppeals,
                bannedWordCount, trend, sectionCounts, rangeDays, activeUsers.size(),
                since.format(formatter), todayDate.format(formatter), rangePosts, topics);
    }

    @Override
    public byte[] overviewCsv(int days) {
        AdminCommunityStatsResponse stats = stats(days);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        StringBuilder csv = new StringBuilder(1024);
        csv.append('\uFEFF');
        appendCsvRow(csv, "社区运营概览导出");
        appendCsvRow(csv, "时间范围", stats.rangeStart() + " ~ " + stats.rangeEnd() + "（近 " + stats.days() + " 天）");
        appendCsvRow(csv, "生成时间", LocalDateTime.now(ZONE).format(formatter));
        appendCsvRow(csv, "");
        appendCsvRow(csv, "指标", "数值", "口径");
        appendCsvRow(csv, "帖子总数", String.valueOf(stats.totalPosts()), "当前正常展示");
        appendCsvRow(csv, "今日新增帖子", String.valueOf(stats.todayPosts()), "自然日");
        appendCsvRow(csv, "评论总数", String.valueOf(stats.totalComments()), "当前正常展示");
        appendCsvRow(csv, "今日新增评论", String.valueOf(stats.todayComments()), "自然日");
        appendCsvRow(csv, "活跃用户", String.valueOf(stats.activeUsers()), "区间内发帖/评论去重（含今日），非留存率");
        appendCsvRow(csv, "区间发帖数", String.valueOf(stats.rangePosts()), "区间内正常展示");
        appendCsvRow(csv, "待处理举报", String.valueOf(stats.pendingReports()), "当前快照");
        appendCsvRow(csv, "隐藏帖子", String.valueOf(stats.hiddenPosts()), "当前快照");
        appendCsvRow(csv, "隐藏评论", String.valueOf(stats.hiddenComments()), "当前快照");
        appendCsvRow(csv, "待处理申诉", String.valueOf(stats.pendingAppeals()), "当前快照");
        appendCsvRow(csv, "屏蔽词数量", String.valueOf(stats.bannedWordCount()), "当前快照");
        appendCsvRow(csv, "");
        appendCsvRow(csv, "每日发帖趋势");
        appendCsvRow(csv, "日期", "发帖数");
        for (AdminCommunityStatsResponse.DayCount day : stats.trend()) {
            appendCsvRow(csv, day.date(), String.valueOf(day.count()));
        }
        appendCsvRow(csv, "");
        appendCsvRow(csv, "分区发帖（区间内正常展示）");
        appendCsvRow(csv, "分区", "帖子数", "占区间发帖比例");
        for (AdminCommunityStatsResponse.SectionCount section : stats.sections()) {
            String share = stats.rangePosts() <= 0 ? "0%"
                    : (Math.round(section.count() * 1000.0 / stats.rangePosts()) / 10.0) + "%";
            appendCsvRow(csv, section.sectionName(), String.valueOf(section.count()), share);
        }
        appendCsvRow(csv, "");
        appendCsvRow(csv, "话题 Top " + TOP_TOPIC_LIMIT + "（区间内正常展示帖子的 topics 标签）");
        appendCsvRow(csv, "话题", "帖子数");
        for (AdminCommunityStatsResponse.TopicCount topic : stats.topics()) {
            appendCsvRow(csv, topic.topic(), String.valueOf(topic.count()));
        }
        appendCsvRow(csv, "");
        appendCsvRow(csv, "口径说明", "活跃用户=区间内发帖/评论去重，非留存/回访率；分区/话题按区间内正常展示帖子统计；"
                + "举报/申诉/隐藏/屏蔽词为当前快照，不随区间变化。");
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private int normalizeDays(int days) {
        if (!SUPPORTED_DAYS.contains(days)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "时间范围仅支持 7 或 30 天");
        }
        return days;
    }

    private long countRangePosts(Long campusId, Long sectionId, LocalDateTime since) {
        Long count = postMapper.selectCount(new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getCampusId, campusId)
                .eq(CommunityPostEntity::getStatus, ENABLED)
                .ge(CommunityPostEntity::getCreatedAt, since)
                .eq(sectionId != null, CommunityPostEntity::getSectionId, sectionId));
        return count == null ? 0 : count;
    }

    /**
     * 区间内帖子话题 Top N：topics 列为逗号分隔标签（writeTopics 写入 "a,b"），按标签逐条计数，
     * 完全使用真实帖子数据，不做热度外推。
     */
    private List<AdminCommunityStatsResponse.TopicCount> topicCounts(Long campusId, LocalDateTime since, int limit) {
        List<Object> values = postMapper.selectObjs(new QueryWrapper<CommunityPostEntity>()
                .select("topics")
                .eq("campus_id", campusId)
                .eq("status", ENABLED)
                .ge("created_at", since)
                .isNotNull("topics"));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object value : values) {
            String raw = value == null ? null : String.valueOf(value);
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            for (String part : raw.split(",")) {
                String topic = part.trim();
                if (!topic.isEmpty()) {
                    counts.merge(topic, 1L, Long::sum);
                }
            }
        }
        List<Map.Entry<String, Long>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((left, right) -> {
            int compared = Long.compare(right.getValue(), left.getValue());
            return compared != 0 ? compared : left.getKey().compareTo(right.getKey());
        });
        return entries.stream()
                .limit(limit)
                .map(entry -> new AdminCommunityStatsResponse.TopicCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void appendCsvRow(StringBuilder csv, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvCell(cells[i]));
        }
        csv.append("\r\n");
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace("\"", "\"\"");
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return "\"" + text + "\"";
        }
        return text;
    }

    @Override
    public PageResult<AdminCommunityPostResponse> pagePosts(int pageNum, int pageSize, String keyword,
                                                            Long sectionId, String status, String postType) {
        Long campusId = campusId();
        LambdaQueryWrapper<CommunityPostEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunityPostEntity::getCampusId, campusId)
                .eq(sectionId != null, CommunityPostEntity::getSectionId, sectionId)
                .eq(StringUtils.hasText(status), CommunityPostEntity::getStatus, upper(status))
                .eq(StringUtils.hasText(postType), CommunityPostEntity::getPostType, upper(postType))
                .and(StringUtils.hasText(keyword), query -> query
                        .like(CommunityPostEntity::getTitle, keyword.trim())
                        .or().like(CommunityPostEntity::getContent, keyword.trim()))
                .orderByDesc(CommunityPostEntity::getCreatedAt);
        Page<CommunityPostEntity> page = postMapper.selectPage(
                new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100)), wrapper);
        Map<Long, String> sectionNames = new LinkedHashMap<>();
        for (CommunitySectionEntity section : sectionsOf(campusId)) {
            sectionNames.put(section.getId(), section.getSectionName());
        }
        Set<Long> authorIds = new HashSet<>();
        page.getRecords().forEach(post -> addId(authorIds, post.getAuthorId()));
        Map<Long, UserEntity> authors = new LinkedHashMap<>();
        if (!authorIds.isEmpty()) {
            userMapper.selectBatchIds(authorIds).forEach(user -> authors.put(user.getId(), user));
        }
        List<AdminCommunityPostResponse> records = page.getRecords().stream().map(post -> {
            UserEntity author = authors.get(post.getAuthorId());
            List<Long> imageIds = JsonIds.parse(post.getImageFileIds());
            String cover = imageIds.isEmpty() ? null : fileStorageService.signThumbnailPath(imageIds.get(0));
            String content = post.getContent() == null ? "" : post.getContent();
            String summary = content.length() > 120 ? content.substring(0, 120) + "…" : content;
            return new AdminCommunityPostResponse(post.getId(), post.getSectionId(),
                    sectionNames.get(post.getSectionId()),
                    post.getPostType() == null ? "DYNAMIC" : post.getPostType(),
                    post.getTitle(), summary, cover, post.getAuthorId(),
                    author == null ? "用户" : author.getDisplayName(), post.getStatus(),
                    post.getHiddenReason(), post.getReportCount() == null ? 0 : post.getReportCount(),
                    post.getCommentCount() == null ? 0 : post.getCommentCount(),
                    post.getLikeCount() == null ? 0 : post.getLikeCount(), post.getCreatedAt(),
                    post.getFeedPinned() != null && post.getFeedPinned() == 1);
        }).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public AdminCommunityPostDetailResponse postDetail(Long postId) {
        CommunityPostEntity post = postMapper.selectByIdIncludingDeleted(postId);
        if (post == null || !campusId().equals(post.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在");
        }
        UserEntity author = userMapper.selectById(post.getAuthorId());
        CommunitySectionEntity section = sectionMapper.selectById(post.getSectionId());
        List<Long> imageIds = JsonIds.parse(post.getImageFileIds());
        return new AdminCommunityPostDetailResponse(post.getId(), post.getSectionId(),
                section == null ? null : section.getSectionName(),
                post.getPostType() == null ? "DYNAMIC" : post.getPostType(),
                post.getTitle(), post.getContent(), post.getStatus(), post.getHiddenReason(),
                post.getHiddenAt(), post.getReportCount() == null ? 0 : post.getReportCount(),
                post.getCommentCount() == null ? 0 : post.getCommentCount(),
                post.getLikeCount() == null ? 0 : post.getLikeCount(),
                post.getAuthorId(), author == null ? "用户" : author.getDisplayName(),
                parseTopics(post.getTopics()), parseBlocks(post.getContentBlocks()),
                imageIds.isEmpty() ? List.of() : fileStorageService.signViewPaths(imageIds),
                imageIds.isEmpty() ? List.of() : imageIds.stream()
                        .map(fileStorageService::signThumbnailPath).toList(),
                post.getCreatedAt());
    }

    @Override
    public List<AdminCommunitySectionResponse> sections() {
        Long campusId = campusId();
        return sectionsOf(campusId).stream().map(section -> toSectionResponse(section,
                countPosts(campusId, ENABLED, section.getId()))).toList();
    }

    @Override
    public AdminCommunitySectionResponse createSection(AdminCommunitySectionRequest request) {
        Long campusId = campusId();
        String code = upper(request.sectionCode());
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "分区编码不能为空");
        }
        Long exists = sectionMapper.selectCount(new LambdaQueryWrapper<CommunitySectionEntity>()
                .eq(CommunitySectionEntity::getCampusId, campusId)
                .eq(CommunitySectionEntity::getSectionCode, code));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "分区编码已存在");
        }
        CommunitySectionEntity entity = new CommunitySectionEntity();
        entity.setCampusId(campusId);
        entity.setSectionCode(code);
        applySection(entity, request);
        sectionMapper.insert(entity);
        return toSectionResponse(entity, 0);
    }

    @Override
    public AdminCommunitySectionResponse updateSection(Long id, AdminCommunitySectionRequest request) {
        CommunitySectionEntity entity = requireSection(id);
        if (StringUtils.hasText(request.sectionCode())
                && !upper(request.sectionCode()).equals(entity.getSectionCode())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "分区编码创建后不可修改");
        }
        CommunitySectionEntity update = new CommunitySectionEntity();
        update.setId(entity.getId());
        applySection(update, request);
        update.setVersion(entity.getVersion());
        sectionMapper.updateById(update);
        CommunitySectionEntity saved = sectionMapper.selectById(id);
        return toSectionResponse(saved, countPosts(campusId(), ENABLED, id));
    }

    @Override
    public void deleteSection(Long id) {
        CommunitySectionEntity entity = requireSection(id);
        long posts = postMapper.selectCount(new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getCampusId, campusId())
                .eq(CommunityPostEntity::getSectionId, entity.getId()));
        if (posts > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "分区下还有帖子，无法删除（可先停用）");
        }
        sectionMapper.deleteById(id);
    }

    @Override
    public List<AdminCommunityTopicResponse> topics() {
        return topicMapper.selectList(new LambdaQueryWrapper<CommunityTopicEntity>()
                        .eq(CommunityTopicEntity::getCampusId, campusId())
                        .orderByAsc(CommunityTopicEntity::getStatus)
                        .orderByDesc(CommunityTopicEntity::getHeat))
                .stream().map(topic -> toTopicResponse(topic, topicPostCount(topic.getName()))).toList();
    }

    @Override
    public AdminCommunityTopicResponse createTopic(AdminCommunityTopicRequest request) {
        Long campusId = campusId();
        String name = request.name().trim();
        Long exists = topicMapper.selectCount(new LambdaQueryWrapper<CommunityTopicEntity>()
                .eq(CommunityTopicEntity::getCampusId, campusId)
                .eq(CommunityTopicEntity::getName, name));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "话题已存在");
        }
        CommunityTopicEntity entity = new CommunityTopicEntity();
        entity.setCampusId(campusId);
        entity.setName(name);
        entity.setHeat(request.heat() == null ? 0 : request.heat());
        entity.setStatus(normalizeStatus(request.status()));
        topicMapper.insert(entity);
        return toTopicResponse(entity, 0);
    }

    @Override
    public AdminCommunityTopicResponse updateTopic(Long id, AdminCommunityTopicRequest request) {
        CommunityTopicEntity entity = topicMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "话题不存在");
        }
        String name = request.name().trim();
        if (!name.equals(entity.getName())) {
            Long exists = topicMapper.selectCount(new LambdaQueryWrapper<CommunityTopicEntity>()
                    .eq(CommunityTopicEntity::getCampusId, entity.getCampusId())
                    .eq(CommunityTopicEntity::getName, name)
                    .ne(CommunityTopicEntity::getId, id));
            if (exists != null && exists > 0) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "话题已存在");
            }
        }
        CommunityTopicEntity update = new CommunityTopicEntity();
        update.setId(id);
        update.setName(name);
        update.setHeat(request.heat() == null ? entity.getHeat() : request.heat());
        update.setStatus(normalizeStatus(request.status()));
        update.setVersion(entity.getVersion());
        topicMapper.updateById(update);
        CommunityTopicEntity saved = topicMapper.selectById(id);
        return toTopicResponse(saved, topicPostCount(saved.getName()));
    }

    @Override
    public void deleteTopic(Long id) {
        if (topicMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "话题不存在");
        }
        topicMapper.deleteById(id);
    }

    @Override
    public List<AdminCommunityBannedWordResponse> bannedWords(String keyword) {
        LambdaQueryWrapper<CommunityBannedWordEntity> wrapper = new LambdaQueryWrapper<CommunityBannedWordEntity>()
                .eq(CommunityBannedWordEntity::getCampusId, campusId())
                .like(StringUtils.hasText(keyword), CommunityBannedWordEntity::getWord,
                        keyword == null ? null : keyword.trim())
                .orderByDesc(CommunityBannedWordEntity::getCreatedAt);
        return bannedWordMapper.selectList(wrapper).stream().map(this::toBannedWordResponse).toList();
    }

    @Override
    public AdminCommunityBannedWordResponse createBannedWord(AdminCommunityBannedWordRequest request) {
        Long campusId = campusId();
        String word = request.word().trim();
        Long exists = bannedWordMapper.selectCount(new LambdaQueryWrapper<CommunityBannedWordEntity>()
                .eq(CommunityBannedWordEntity::getCampusId, campusId)
                .eq(CommunityBannedWordEntity::getWord, word));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "屏蔽词已存在");
        }
        String matchType = normalizeMatchType(request.matchType(), word);
        CommunityBannedWordEntity entity = new CommunityBannedWordEntity();
        entity.setCampusId(campusId);
        entity.setWord(word);
        entity.setRemark(request.remark());
        entity.setStatus(normalizeStatus(request.status()));
        entity.setMatchType(matchType);
        entity.setHitCount(0);
        bannedWordMapper.insert(entity);
        bannedWordService.invalidate(campusId);
        return toBannedWordResponse(entity);
    }

    @Override
    public AdminCommunityBannedWordResponse updateBannedWord(Long id, AdminCommunityBannedWordRequest request) {
        CommunityBannedWordEntity entity = bannedWordMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "屏蔽词不存在");
        }
        String word = request.word().trim();
        CommunityBannedWordEntity update = new CommunityBannedWordEntity();
        update.setId(id);
        update.setWord(word);
        update.setRemark(request.remark());
        update.setStatus(normalizeStatus(request.status()));
        update.setMatchType(normalizeMatchType(request.matchType(), word));
        update.setVersion(entity.getVersion());
        bannedWordMapper.updateById(update);
        bannedWordService.invalidate(entity.getCampusId());
        return toBannedWordResponse(bannedWordMapper.selectById(id));
    }

    @Override
    public void deleteBannedWord(Long id) {
        CommunityBannedWordEntity entity = bannedWordMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "屏蔽词不存在");
        }
        bannedWordMapper.deleteById(id);
        bannedWordService.invalidate(entity.getCampusId());
    }

    @Override
    public byte[] exportBannedWords() {
        Long campusId = campusId();
        List<CommunityBannedWordEntity> words = bannedWordMapper.selectList(
                new LambdaQueryWrapper<CommunityBannedWordEntity>()
                        .eq(CommunityBannedWordEntity::getCampusId, campusId)
                        .orderByDesc(CommunityBannedWordEntity::getCreatedAt));
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("屏蔽词");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("屏蔽词");
            header.createCell(1).setCellValue("备注");
            header.createCell(2).setCellValue("状态（启用/停用）");
            header.createCell(3).setCellValue("匹配方式（LITERAL/REGEX）");
            header.createCell(4).setCellValue("命中次数");
            int rowIndex = 1;
            for (CommunityBannedWordEntity word : words) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(word.getWord());
                row.createCell(1).setCellValue(word.getRemark() == null ? "" : word.getRemark());
                row.createCell(2).setCellValue(ENABLED.equals(word.getStatus()) ? "启用" : "停用");
                row.createCell(3).setCellValue(word.getMatchType() == null ? "LITERAL" : word.getMatchType());
                row.createCell(4).setCellValue(word.getHitCount() == null ? 0 : word.getHitCount());
            }
            sheet.setColumnWidth(0, 24 * 256);
            sheet.setColumnWidth(1, 40 * 256);
            sheet.setColumnWidth(2, 16 * 256);
            sheet.setColumnWidth(3, 24 * 256);
            sheet.setColumnWidth(4, 12 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception exception) {
            log.error("导出屏蔽词失败", exception);
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "导出失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminBannedWordImportResult importBannedWords(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请选择 Excel 文件");
        }
        Long campusId = campusId();
        int total = 0;
        int imported = 0;
        int duplicated = 0;
        int invalid = 0;
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String word = formatter.formatCellValue(row.getCell(0)).trim();
                if (word.isEmpty()) {
                    continue;
                }
                total++;
                if (word.length() > 64) {
                    invalid++;
                    continue;
                }
                Long exists = bannedWordMapper.selectCount(new LambdaQueryWrapper<CommunityBannedWordEntity>()
                        .eq(CommunityBannedWordEntity::getCampusId, campusId)
                        .eq(CommunityBannedWordEntity::getWord, word));
                if (exists != null && exists > 0) {
                    duplicated++;
                    continue;
                }
                Cell remarkCell = row.getCell(1);
                Cell statusCell = row.getCell(2);
                Cell matchTypeCell = row.getCell(3);
                String remark = formatter.formatCellValue(remarkCell).trim();
                String matchType = formatter.formatCellValue(matchTypeCell).trim().toUpperCase();
                try {
                    matchType = normalizeMatchType(matchType, word);
                } catch (BusinessException exception) {
                    invalid++;
                    continue;
                }
                CommunityBannedWordEntity entity = new CommunityBannedWordEntity();
                entity.setCampusId(campusId);
                entity.setWord(word);
                entity.setRemark(remark.isEmpty() ? null : remark);
                entity.setStatus(parseStatusText(formatter.formatCellValue(statusCell)));
                entity.setMatchType(matchType);
                entity.setHitCount(0);
                bannedWordMapper.insert(entity);
                imported++;
            }
        } catch (Exception exception) {
            log.warn("屏蔽词导入失败, msg={}", exception.getMessage());
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "Excel 解析失败，请使用导出的模板（首行为表头）");
        }
        bannedWordService.invalidate(campusId);
        log.info("屏蔽词导入完成, campusId={}, total={}, imported={}, duplicated={}, invalid={}",
                campusId, total, imported, duplicated, invalid);
        return new AdminBannedWordImportResult(total, imported, duplicated, invalid);
    }

    private String parseStatusText(String text) {
        if (!StringUtils.hasText(text)) {
            return ENABLED;
        }
        String value = text.trim();
        if ("停用".equals(value) || "DISABLED".equalsIgnoreCase(value) || "0".equals(value)) {
            return "DISABLED";
        }
        return ENABLED;
    }

    @Override
    public PageResult<AdminCommunityAppealResponse> pageAppeals(int pageNum, int pageSize, String status, String targetType) {
        LambdaQueryWrapper<CommunityAppealEntity> wrapper = new LambdaQueryWrapper<CommunityAppealEntity>()
                .eq(CommunityAppealEntity::getCampusId, campusId())
                .eq(StringUtils.hasText(status), CommunityAppealEntity::getStatus, upper(status))
                .eq(StringUtils.hasText(targetType), CommunityAppealEntity::getTargetType, upper(targetType))
                .orderByAsc(CommunityAppealEntity::getStatus)
                .orderByDesc(CommunityAppealEntity::getCreatedAt);
        Page<CommunityAppealEntity> page = appealMapper.selectPage(
                new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100)), wrapper);
        Set<Long> userIds = new HashSet<>();
        page.getRecords().forEach(appeal -> addId(userIds, appeal.getUserId()));
        Map<Long, UserEntity> users = new LinkedHashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(user -> users.put(user.getId(), user));
        }
        List<AdminCommunityAppealResponse> records = page.getRecords().stream().map(appeal -> {
            UserEntity user = users.get(appeal.getUserId());
            LocalDateTime dueAt = dueAtOf(appeal.getCreatedAt(), appeal.getDueAt());
            long remaining = remainingMinutes(dueAt);
            return new AdminCommunityAppealResponse(appeal.getId(), appeal.getUserId(),
                    user == null ? "用户" : user.getDisplayName(), appeal.getTargetType(), appeal.getTargetId(),
                    targetTitle(appeal), appeal.getReasonCode(), appeal.getReason(), appeal.getEvidenceFileIds(),
                    signEvidence(appeal.getEvidenceFileIds()),
                    appeal.getStatus(), appeal.getHandledBy(), appeal.getHandledAt(), appeal.getHandleNote(),
                    appeal.getCreatedAt(), dueAt,
                    PENDING.equals(appeal.getStatus()) && remaining < 0, remaining);
        }).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAppeal(Long appealId, AdminCommunityAppealHandleRequest request) {
        CommunityAppealEntity appeal = appealMapper.selectById(appealId);
        if (appeal == null || !campusId().equals(appeal.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "申诉不存在");
        }
        if (!PENDING.equals(appeal.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该申诉已处理");
        }
        String action = upper(request.action());
        boolean approve = "APPROVE".equals(action);
        if (!approve && !"REJECT".equals(action)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "处理结论只能是 APPROVE 或 REJECT");
        }
        Long adminId = currentUserProvider.requireCurrentUser().userId();
        String note = resolveTemplateNote(request.note(), request.templateId(),
                CommunityModerationTemplateEntity.TYPE_APPEAL_REPLY);
        if (approve) {
            restoreTarget(appeal, adminId);
        }
        CommunityAppealEntity update = new CommunityAppealEntity();
        update.setId(appeal.getId());
        update.setStatus(approve ? APPROVED : REJECTED);
        update.setHandledBy(adminId);
        update.setHandledAt(LocalDateTime.now(ZONE));
        update.setHandleNote(note);
        update.setVersion(appeal.getVersion());
        appealMapper.updateById(update);
        String content = approve
                ? "你的社区申诉已通过，相关处理已恢复。"
                        + (StringUtils.hasText(note) ? "（复核说明：" + note + "）" : "")
                : "你的社区申诉未通过" + (StringUtils.hasText(note) ? "：" + note : "。");
        notificationService.send(new NotificationSendCommand(appeal.getUserId(), "COMMUNITY_MODERATION",
                "申诉处理结果", content, "COMMUNITY_POST", appeal.getTargetId()));
    }

    private void restoreTarget(CommunityAppealEntity appeal, Long adminId) {
        if (TARGET_POST.equals(appeal.getTargetType())) {
            CommunityPostEntity post = postMapper.selectByIdIncludingDeleted(appeal.getTargetId());
            if (post == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "申诉对应的帖子不存在");
            }
            postMapper.restoreFromAppeal(post.getId(), adminId);
        } else if (TARGET_COMMENT.equals(appeal.getTargetType())) {
            CommunityCommentEntity comment = commentMapper.selectById(appeal.getTargetId());
            if (comment == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "申诉对应的评论不存在");
            }
            CommunityCommentEntity update = new CommunityCommentEntity();
            update.setId(comment.getId());
            update.setStatus(ENABLED);
            update.setHiddenReason(null);
            update.setVersion(comment.getVersion());
            commentMapper.updateById(update);
        } else if (TARGET_SANCTION.equals(appeal.getTargetType())) {
            CommunityUserSanctionEntity sanction = sanctionMapper.selectById(appeal.getTargetId());
            if (sanction == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "申诉对应的处罚不存在");
            }
            CommunityUserSanctionEntity update = new CommunityUserSanctionEntity();
            update.setId(sanction.getId());
            update.setStatus("REVOKED");
            update.setVersion(sanction.getVersion());
            sanctionMapper.updateById(update);
        } else {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "申诉对象类型不合法");
        }
    }

    private String targetTitle(CommunityAppealEntity appeal) {
        if (TARGET_POST.equals(appeal.getTargetType())) {
            CommunityPostEntity post = postMapper.selectByIdIncludingDeleted(appeal.getTargetId());
            return post == null ? "帖子#" + appeal.getTargetId() : post.getTitle();
        }
        if (TARGET_COMMENT.equals(appeal.getTargetType())) {
            CommunityCommentEntity comment = commentMapper.selectById(appeal.getTargetId());
            if (comment == null) {
                return "评论#" + appeal.getTargetId();
            }
            String content = comment.getContent() == null ? "" : comment.getContent();
            return content.length() > 40 ? content.substring(0, 40) + "…" : content;
        }
        if (TARGET_SANCTION.equals(appeal.getTargetType())) {
            CommunityUserSanctionEntity sanction = sanctionMapper.selectById(appeal.getTargetId());
            return sanction == null ? "处罚#" + appeal.getTargetId() : "账号处罚：" + sanction.getReason();
        }
        return "-";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminCommunityBatchResult batchPostStatus(AdminCommunityBatchPostStatusRequest request) {
        String action = request.action().trim().toUpperCase();
        if (!"HIDE".equals(action) && !"RESTORE".equals(action)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "批量动作仅支持 HIDE / RESTORE");
        }
        boolean hide = "HIDE".equals(action);
        Long campusId = campusId();
        int requested = 0;
        int updated = 0;
        List<String> skippedReasons = new ArrayList<>();
        List<Map<String, Object>> before = new ArrayList<>();
        for (Long postId : new java.util.LinkedHashSet<>(request.postIds())) {
            if (postId == null) {
                continue;
            }
            requested++;
            CommunityPostEntity post = postMapper.selectById(postId);
            if (post == null || !campusId.equals(post.getCampusId())) {
                addSkip(skippedReasons, "帖子 #" + postId + " 不存在或不属于当前校区");
                continue;
            }
            if (hide && HIDDEN.equals(post.getStatus())) {
                addSkip(skippedReasons, "帖子 #" + postId + " 已下架");
                continue;
            }
            if (!hide && ENABLED.equals(post.getStatus())) {
                addSkip(skippedReasons, "帖子 #" + postId + " 未下架，无需恢复");
                continue;
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("postId", post.getId());
            snapshot.put("status", post.getStatus());
            snapshot.put("hiddenReason", post.getHiddenReason());
            before.add(snapshot);

            // 用 UpdateWrapper 显式置空 hidden_reason/hidden_at：updateById 会忽略 null 字段
            LambdaUpdateWrapper<CommunityPostEntity> postUpdate = new LambdaUpdateWrapper<CommunityPostEntity>()
                    .eq(CommunityPostEntity::getId, post.getId())
                    .set(CommunityPostEntity::getStatus, hide ? HIDDEN : ENABLED)
                    .set(CommunityPostEntity::getHiddenReason, hide ? (StringUtils.hasText(request.reason())
                            ? request.reason().trim() : "管理端批量处置") : null)
                    .set(CommunityPostEntity::getHiddenAt, hide ? LocalDateTime.now(ZONE) : null)
                    .setSql("version = version + 1");
            postMapper.update(null, postUpdate);
            updated++;

            if (hide) {
                notificationService.send(new NotificationSendCommand(post.getAuthorId(), "COMMUNITY_MODERATION",
                        "帖子批量处置通知", "你的帖子「" + post.getTitle() + "」因"
                                + (StringUtils.hasText(request.reason()) ? request.reason().trim() : "违反社区规范")
                                + "已被下架，如认为处理有误可提交申诉。", "COMMUNITY_POST", post.getId()));
            } else {
                notificationService.send(new NotificationSendCommand(post.getAuthorId(), "COMMUNITY_MODERATION",
                        "帖子已恢复", "你的帖子「" + post.getTitle() + "」已恢复展示。",
                        "COMMUNITY_POST", post.getId()));
            }
        }
        AuditTrailContext.setBefore(Map.of("action", action, "posts", before));
        log.info("帖子批量处置完成, action={}, requested={}, updated={}, skipped={}",
                action, requested, updated, skippedReasons.size());
        return new AdminCommunityBatchResult(requested, updated, skippedReasons.size(),
                skippedReasons.size() > 20 ? skippedReasons.subList(0, 20) : skippedReasons);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminCommunityBatchResult reorderSections(AdminCommunitySectionReorderRequest request) {
        Long campusId = campusId();
        List<Long> ids = new ArrayList<>(new java.util.LinkedHashSet<>(request.sectionIds()));
        List<CommunitySectionEntity> sections = sectionMapper.selectBatchIds(ids);
        Map<Long, CommunitySectionEntity> byId = new LinkedHashMap<>();
        sections.forEach(section -> byId.put(section.getId(), section));
        List<Map<String, Object>> before = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int updated = 0;
        for (Long id : ids) {
            CommunitySectionEntity section = byId.get(id);
            if (section == null || !campusId.equals(section.getCampusId())) {
                addSkip(skipped, "分区 #" + id + " 不存在或不属于当前校区");
                continue;
            }
            before.add(Map.of("sectionId", section.getId(), "sortOrder",
                    section.getSortOrder() == null ? 0 : section.getSortOrder()));
            CommunitySectionEntity update = new CommunitySectionEntity();
            update.setId(section.getId());
            update.setSortOrder(ids.indexOf(id) + 1);
            update.setVersion(section.getVersion());
            sectionMapper.updateById(update);
            updated++;
        }
        AuditTrailContext.setBefore(Map.of("sections", before));
        return new AdminCommunityBatchResult(ids.size(), updated, skipped.size(), skipped);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminCommunityTopicResponse recommendTopic(Long id, boolean recommended) {
        CommunityTopicEntity entity = requireTopic(id);
        CommunityTopicEntity update = new CommunityTopicEntity();
        update.setId(id);
        update.setRecommended(recommended);
        update.setVersion(entity.getVersion());
        topicMapper.updateById(update);
        CommunityTopicEntity saved = topicMapper.selectById(id);
        return toTopicResponse(saved, topicPostCount(saved.getName()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminCommunityTopicResponse mergeTopic(Long sourceTopicId, Long targetTopicId) {
        if (sourceTopicId == null || targetTopicId == null || sourceTopicId.equals(targetTopicId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "源话题与目标话题不能相同");
        }
        CommunityTopicEntity source = requireTopic(sourceTopicId);
        CommunityTopicEntity target = requireTopic(targetTopicId);
        if (source.getMergedIntoId() != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该话题已合并到其他话题");
        }
        if (target.getMergedIntoId() != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "目标话题已合并，不能作为合并目标");
        }
        Long campusId = campusId();
        List<CommunityPostEntity> posts = postMapper.selectList(new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getCampusId, campusId)
                .like(CommunityPostEntity::getTopics, source.getName())
                .last("LIMIT 500"));
        int moved = 0;
        for (CommunityPostEntity post : posts) {
            String mergedTopics = mergeTopicNames(post.getTopics(), source.getName(), target.getName());
            if (mergedTopics == null || mergedTopics.equals(post.getTopics())) {
                continue;
            }
            CommunityPostEntity update = new CommunityPostEntity();
            update.setId(post.getId());
            update.setTopics(mergedTopics);
            update.setVersion(post.getVersion());
            postMapper.updateById(update);
            moved++;
        }
        CommunityTopicEntity sourceUpdate = new CommunityTopicEntity();
        sourceUpdate.setId(source.getId());
        sourceUpdate.setStatus("DISABLED");
        sourceUpdate.setRecommended(false);
        sourceUpdate.setMergedIntoId(target.getId());
        sourceUpdate.setVersion(source.getVersion());
        topicMapper.updateById(sourceUpdate);

        CommunityTopicEntity targetUpdate = new CommunityTopicEntity();
        targetUpdate.setId(target.getId());
        targetUpdate.setHeat((target.getHeat() == null ? 0 : target.getHeat())
                + (source.getHeat() == null ? 0 : source.getHeat()));
        targetUpdate.setVersion(target.getVersion());
        topicMapper.updateById(targetUpdate);

        AuditTrailContext.setBefore(Map.of("sourceTopicId", sourceTopicId, "sourceName", source.getName(),
                "targetTopicId", targetTopicId, "movedPosts", moved));
        log.info("话题合并完成, source={}, target={}, movedPosts={}", sourceTopicId, targetTopicId, moved);
        CommunityTopicEntity saved = topicMapper.selectById(targetTopicId);
        return toTopicResponse(saved, topicPostCount(saved.getName()));
    }

    @Override
    public AdminCommunityTopicStatsResponse topicStats(Long id) {
        CommunityTopicEntity topic = requireTopic(id);
        long total = topicPostCount(topic.getName());
        Long recent = postMapper.selectCount(new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getCampusId, campusId())
                .like(CommunityPostEntity::getTopics, topic.getName())
                .ge(CommunityPostEntity::getCreatedAt, LocalDate.now(ZONE).minusDays(6).atStartOfDay()));
        return new AdminCommunityTopicStatsResponse(topic.getId(), topic.getName(), total,
                recent == null ? 0 : recent, Boolean.TRUE.equals(topic.getRecommended()),
                topic.getMergedIntoId());
    }

    @Override
    public List<AdminModerationTemplateResponse> templates(String templateType) {
        String type = StringUtils.hasText(templateType) ? templateType.trim().toUpperCase() : null;
        if (type != null && !TEMPLATE_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板类型仅支持 REPORT_NOTE/APPEAL_REPLY");
        }
        Long campusId = campusId();
        return templateMapper.selectList(new LambdaQueryWrapper<CommunityModerationTemplateEntity>()
                        .in(CommunityModerationTemplateEntity::getCampusId, List.of(0L, campusId))
                        .eq(type != null, CommunityModerationTemplateEntity::getTemplateType, type)
                        .eq(CommunityModerationTemplateEntity::getStatus, ENABLED)
                        .orderByAsc(CommunityModerationTemplateEntity::getSortOrder)
                        .orderByAsc(CommunityModerationTemplateEntity::getId))
                .stream().map(this::toTemplateResponse).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminModerationTemplateResponse createTemplate(AdminModerationTemplateRequest request) {
        CommunityModerationTemplateEntity entity = new CommunityModerationTemplateEntity();
        entity.setCampusId(campusId());
        applyTemplate(entity, request);
        templateMapper.insert(entity);
        return toTemplateResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminModerationTemplateResponse updateTemplate(Long id, AdminModerationTemplateRequest request) {
        CommunityModerationTemplateEntity entity = templateMapper.selectById(id);
        if (entity == null || !campusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在或为平台内置模板（不可修改）");
        }
        CommunityModerationTemplateEntity update = new CommunityModerationTemplateEntity();
        update.setId(id);
        applyTemplate(update, request);
        update.setVersion(entity.getVersion());
        templateMapper.updateById(update);
        return toTemplateResponse(templateMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        CommunityModerationTemplateEntity entity = templateMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        if (entity.getCampusId() == null || entity.getCampusId() == 0L) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "平台内置模板不可删除");
        }
        if (!campusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在或不属于当前校区");
        }
        templateMapper.deleteById(id);
    }

    private void applyTemplate(CommunityModerationTemplateEntity entity, AdminModerationTemplateRequest request) {
        String type = request.templateType().trim().toUpperCase();
        if (!TEMPLATE_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板类型仅支持 REPORT_NOTE/APPEAL_REPLY");
        }
        String reasonCode = StringUtils.hasText(request.reasonCode())
                ? request.reasonCode().trim().toUpperCase() : null;
        if (reasonCode != null && !TEMPLATE_REASON_CODES.contains(reasonCode)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "原因编码不合法");
        }
        entity.setTemplateType(type);
        entity.setTemplateName(request.templateName().trim());
        entity.setReasonCode(reasonCode);
        entity.setContent(request.content().trim());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setStatus(normalizeStatus(request.status()));
    }

    private AdminModerationTemplateResponse toTemplateResponse(CommunityModerationTemplateEntity entity) {
        boolean builtin = entity.getCampusId() == null || entity.getCampusId() == 0L;
        return new AdminModerationTemplateResponse(entity.getId(), entity.getCampusId(), entity.getTemplateType(),
                entity.getTemplateName(), entity.getReasonCode(), entity.getContent(), entity.getSortOrder(),
                entity.getStatus(), builtin, entity.getCreatedAt());
    }

    private CommunityTopicEntity requireTopic(Long id) {
        CommunityTopicEntity entity = topicMapper.selectById(id);
        if (entity == null || (entity.getCampusId() != null && !campusId().equals(entity.getCampusId()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "话题不存在或不属于当前校区");
        }
        return entity;
    }

    /** 逗号分隔的话题标签替换；返回 null 表示未命中需要替换 */
    private String mergeTopicNames(String topics, String source, String target) {
        if (!StringUtils.hasText(topics)) {
            return null;
        }
        List<String> names = new ArrayList<>();
        boolean changed = false;
        for (String item : topics.split(",")) {
            String name = item.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (name.equals(source)) {
                name = target;
                changed = true;
            }
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return changed ? String.join(",", names) : null;
    }

    private static void addSkip(List<String> reasons, String reason) {
        if (reasons.size() < 100) {
            reasons.add(reason);
        }
    }

    private static LocalDateTime dueAtOf(LocalDateTime createdAt, LocalDateTime dueAt) {
        if (dueAt != null) {
            return dueAt;
        }
        return createdAt == null ? null : createdAt.plusHours(24);
    }

    private static long remainingMinutes(LocalDateTime dueAt) {
        if (dueAt == null) {
            return 0;
        }
        return Duration.between(LocalDateTime.now(ZONE), dueAt).toMinutes();
    }

    private List<String> signEvidence(String evidenceFileIds) {
        if (!StringUtils.hasText(evidenceFileIds)) {
            return List.of();
        }
        try {
            List<Long> ids = objectMapper.readValue(evidenceFileIds, new TypeReference<List<Long>>() {
            });
            return ids.isEmpty() ? List.of() : fileStorageService.signViewPaths(ids);
        } catch (Exception exception) {
            return List.of();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminCommunityBatchResult batchHandleAppeals(AdminCommunityBatchAppealHandleRequest request) {
        String action = upper(request.action());
        if (!"APPROVE".equals(action) && !REJECTED.equals(action)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "批量复核结论只能是 APPROVE 或 REJECT");
        }
        String note = resolveTemplateNote(request.note(), request.templateId(),
                CommunityModerationTemplateEntity.TYPE_APPEAL_REPLY);
        Long adminId = currentUserProvider.requireCurrentUser().userId();
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>(request.appealIds());
        int updated = 0;
        List<String> skipped = new ArrayList<>();
        for (Long appealId : ids) {
            if (appealId == null) {
                continue;
            }
            CommunityAppealEntity appeal = appealMapper.selectById(appealId);
            if (appeal == null || !campusId().equals(appeal.getCampusId())) {
                addSkip(skipped, "申诉 #" + appealId + "：不存在或不属于当前校区");
                continue;
            }
            if (!PENDING.equals(appeal.getStatus())) {
                addSkip(skipped, "申诉 #" + appealId + "：已处理");
                continue;
            }
            boolean approve = "APPROVE".equals(action);
            if (approve) {
                restoreTarget(appeal, adminId);
            }
            CommunityAppealEntity update = new CommunityAppealEntity();
            update.setId(appeal.getId());
            update.setStatus(approve ? APPROVED : REJECTED);
            update.setHandledBy(adminId);
            update.setHandledAt(LocalDateTime.now(ZONE));
            update.setHandleNote(note);
            update.setVersion(appeal.getVersion());
            appealMapper.updateById(update);
            notificationService.send(new NotificationSendCommand(appeal.getUserId(), "COMMUNITY_MODERATION",
                    "申诉处理结果",
                    approve ? "你的社区申诉已通过，相关处理已恢复。"
                            + (StringUtils.hasText(note) ? "（复核说明：" + note + "）" : "")
                            : "你的社区申诉未通过" + (StringUtils.hasText(note) ? "：" + note : "。"),
                    "COMMUNITY_POST", appeal.getTargetId()));
            updated++;
        }
        return new AdminCommunityBatchResult(ids.size(), updated, skipped.size(),
                skipped.size() > 20 ? skipped.subList(0, 20) : skipped);
    }

    /** 模板正文优先；手填说明存在时保留人工补充 */
    private String resolveTemplateNote(String note, Long templateId, String templateType) {
        if (templateId == null) {
            return note;
        }
        CommunityModerationTemplateEntity template = templateMapper.selectById(templateId);
        if (template == null || !templateType.equals(template.getTemplateType())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板不存在或类型不匹配");
        }
        if (!StringUtils.hasText(note)) {
            return template.getContent();
        }
        return template.getContent() + " " + note;
    }

    private long countPosts(Long campusId, String status, Long sectionId) {
        return postMapper.selectCount(new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getCampusId, campusId)
                .eq(CommunityPostEntity::getStatus, status)
                .eq(sectionId != null, CommunityPostEntity::getSectionId, sectionId));
    }

    private List<CommunitySectionEntity> sectionsOf(Long campusId) {
        return sectionMapper.selectList(new LambdaQueryWrapper<CommunitySectionEntity>()
                .eq(CommunitySectionEntity::getCampusId, campusId)
                .orderByAsc(CommunitySectionEntity::getSortOrder));
    }

    private CommunitySectionEntity requireSection(Long id) {
        CommunitySectionEntity entity = sectionMapper.selectById(id);
        if (entity == null || !campusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分区不存在");
        }
        return entity;
    }

    private void applySection(CommunitySectionEntity entity, AdminCommunitySectionRequest request) {
        entity.setSectionName(request.sectionName().trim());
        entity.setIcon(request.icon() == null ? "cat-default" : request.icon().trim());
        entity.setDescription(request.description());
        entity.setBannerTitle(request.bannerTitle());
        entity.setBannerSubtitle(request.bannerSubtitle());
        entity.setBannerImage(request.bannerImage());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setRecommendWeight(request.recommendWeight() == null ? 0 : request.recommendWeight());
        entity.setStatus(normalizeStatus(request.status()));
    }

    private AdminCommunitySectionResponse toSectionResponse(CommunitySectionEntity entity, long postCount) {
        return new AdminCommunitySectionResponse(entity.getId(), entity.getSectionCode(), entity.getSectionName(),
                entity.getIcon(), entity.getDescription(), entity.getBannerTitle(), entity.getBannerSubtitle(),
                entity.getBannerImage(), entity.getSortOrder(), entity.getStatus(), postCount,
                entity.getRecommendWeight() == null ? 0 : entity.getRecommendWeight());
    }

    private AdminCommunityTopicResponse toTopicResponse(CommunityTopicEntity entity, long postCount) {
        return new AdminCommunityTopicResponse(entity.getId(), entity.getName(), entity.getHeat(), entity.getStatus(),
                Boolean.TRUE.equals(entity.getRecommended()), entity.getMergedIntoId(), postCount);
    }

    /** 话题关联帖子数：community_post.topics 以逗号分隔存储话题名 */
    private long topicPostCount(String topicName) {
        if (!StringUtils.hasText(topicName)) {
            return 0;
        }
        Long count = postMapper.selectCount(new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getCampusId, campusId())
                .like(CommunityPostEntity::getTopics, topicName));
        return count == null ? 0 : count;
    }

    private AdminCommunityBannedWordResponse toBannedWordResponse(CommunityBannedWordEntity entity) {
        return new AdminCommunityBannedWordResponse(entity.getId(), entity.getWord(), entity.getRemark(),
                entity.getStatus(), entity.getMatchType() == null ? "LITERAL" : entity.getMatchType(),
                entity.getHitCount() == null ? 0 : entity.getHitCount(), entity.getLastHitAt(),
                entity.getCreatedAt());
    }

    /** 匹配方式校验：REGEX 需通过安全校验；缺省 LITERAL */
    private String normalizeMatchType(String matchType, String word) {
        String type = !StringUtils.hasText(matchType) ? "LITERAL" : matchType.trim().toUpperCase();
        if (!"LITERAL".equals(type) && !"REGEX".equals(type)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "匹配方式仅支持 LITERAL/REGEX");
        }
        if ("REGEX".equals(type)) {
            BannedWordService.validatePattern(word);
        }
        return type;
    }

    private List<String> parseTopics(String topics) {
        if (!StringUtils.hasText(topics)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(topics, new TypeReference<List<String>>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    /** 文章块解析与签名（与小程序详情同口径） */
    private List<CommunityContentBlockResponse> parseBlocks(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Block> blocks = objectMapper.readValue(json, new TypeReference<List<Block>>() {
            });
            List<Long> imageIds = blocks.stream()
                    .filter(block -> "IMAGE".equalsIgnoreCase(block.type()) && block.fileId() != null)
                    .map(Block::fileId).distinct().toList();
            List<String> urls = imageIds.isEmpty() ? List.of() : fileStorageService.signViewPaths(imageIds);
            List<String> thumbnails = imageIds.isEmpty() ? List.of()
                    : imageIds.stream().map(fileStorageService::signThumbnailPath).toList();
            Map<Long, Integer> index = new LinkedHashMap<>();
            for (int i = 0; i < imageIds.size(); i++) {
                index.put(imageIds.get(i), i);
            }
            List<CommunityContentBlockResponse> result = new ArrayList<>();
            for (Block block : blocks) {
                if ("IMAGE".equalsIgnoreCase(block.type()) && block.fileId() != null) {
                    Integer position = index.get(block.fileId());
                    result.add(new CommunityContentBlockResponse("IMAGE", null, block.fileId(),
                            position == null ? null : urls.get(position),
                            position == null ? null : thumbnails.get(position)));
                } else if ("TEXT".equalsIgnoreCase(block.type())) {
                    result.add(new CommunityContentBlockResponse("TEXT", block.text(), null, null, null));
                }
            }
            return result;
        } catch (Exception exception) {
            log.warn("管理端帖子内容块解析失败");
            return List.of();
        }
    }

    private Long campusId() {
        return campusContextService.currentCampusId();
    }

    private static void addId(Set<Long> ids, Object value) {
        if (value instanceof Number number) {
            ids.add(number.longValue());
        } else if (value instanceof String text && text.matches("\\d+")) {
            ids.add(Long.parseLong(text));
        }
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return ENABLED;
        }
        String value = status.trim().toUpperCase();
        if (!ENABLED.equals(value) && !"DISABLED".equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "状态仅支持 ENABLED/DISABLED");
        }
        return value;
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private record Block(String type, String text, Long fileId) {
    }
}
