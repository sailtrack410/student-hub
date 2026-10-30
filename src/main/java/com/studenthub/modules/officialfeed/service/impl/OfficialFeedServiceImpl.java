package com.studenthub.modules.officialfeed.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.common.util.JsonIds;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.controller.dto.CommunityContentBlockResponse;
import com.studenthub.modules.community.entity.CommunityPostEntity;
import com.studenthub.modules.community.entity.CommunitySectionEntity;
import com.studenthub.modules.community.mapper.CommunityPostMapper;
import com.studenthub.modules.community.mapper.CommunitySectionMapper;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedArticleResponse;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedArticleUpdateRequest;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedImportRequest;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedPreviewResponse;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedSourceRequest;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedSourceResponse;
import com.studenthub.modules.officialfeed.controller.dto.OfficialFeedSyncResult;
import com.studenthub.modules.officialfeed.controller.dto.OfficialUserResponse;
import com.studenthub.modules.officialfeed.entity.OfficialFeedArticleEntity;
import com.studenthub.modules.officialfeed.entity.OfficialFeedSourceEntity;
import com.studenthub.modules.officialfeed.integration.OfficialSiteClient;
import com.studenthub.modules.officialfeed.mapper.OfficialFeedArticleMapper;
import com.studenthub.modules.officialfeed.mapper.OfficialFeedSourceMapper;
import com.studenthub.modules.officialfeed.service.OfficialFeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialFeedServiceImpl implements OfficialFeedService {

    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_CONTENT_LENGTH = 2_000;
    private static final int MAX_BLOCK_TEXT_LENGTH = 10_000;
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final OfficialFeedSourceMapper sourceMapper;
    private final OfficialFeedArticleMapper articleMapper;
    private final CommunityPostMapper postMapper;
    private final CommunitySectionMapper sectionMapper;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;
    private final OfficialSiteClient siteClient;
    private final ObjectMapper objectMapper;
    private final Set<Long> syncing = ConcurrentHashMap.newKeySet();
    private final AdminCampusContextService campusContextService;

    @Override
    public List<AdminOfficialFeedSourceResponse> listSources() {
        List<OfficialFeedSourceEntity> sources = sourceMapper.selectList(new LambdaQueryWrapper<OfficialFeedSourceEntity>()
                .eq(OfficialFeedSourceEntity::getCampusId, campusContextService.currentCampusId())
                .orderByAsc(OfficialFeedSourceEntity::getId));
        if (sources.isEmpty()) {
            return List.of();
        }
        Map<Long, CommunitySectionEntity> sections = sectionMapper.selectBatchIds(
                        sources.stream().map(OfficialFeedSourceEntity::getSectionId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CommunitySectionEntity::getId, Function.identity(), (a, b) -> a));
        Map<Long, UserEntity> authors = userMapper.selectBatchIds(
                        sources.stream().map(OfficialFeedSourceEntity::getAuthorId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(UserEntity::getId, Function.identity(), (a, b) -> a));
        return sources.stream().map(source -> toResponse(source, sections.get(source.getSectionId()),
                authors.get(source.getAuthorId()))).toList();
    }

    @Override
    public List<OfficialUserResponse> officialUsers() {
        // 官方抓取号必须为 OFFICIAL_AUTO；人工运营号/广告服务号不能作为抓取发布主体。
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getAccountType, "OFFICIAL_AUTO")
                        .eq(UserEntity::getCampusId, campusContextService.currentCampusId())
                        .orderByAsc(UserEntity::getId))
                .stream().map(user -> new OfficialUserResponse(user.getId(), user.getDisplayName(),
                        user.getOfficialTitle())).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOfficialFeedSourceResponse createSource(AdminOfficialFeedSourceRequest request) {
        validateRequest(request);
        OfficialFeedSourceEntity entity = new OfficialFeedSourceEntity();
        applyRequest(entity, request);
        entity.setCampusId(campusContextService.currentCampusId());
        sourceMapper.insert(entity);
        return toResponse(entity, sectionMapper.selectById(entity.getSectionId()),
                userMapper.selectById(entity.getAuthorId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOfficialFeedSourceResponse updateSource(Long sourceId, AdminOfficialFeedSourceRequest request) {
        OfficialFeedSourceEntity entity = requireSource(sourceId);
        validateRequest(request);
        applyRequest(entity, request);
        sourceMapper.updateById(entity);
        return toResponse(entity, sectionMapper.selectById(entity.getSectionId()),
                userMapper.selectById(entity.getAuthorId()));
    }

    @Override
    public OfficialFeedSyncResult sync(Long sourceId) {
        OfficialFeedSourceEntity source = requireSource(sourceId);
        if (!syncing.add(sourceId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该来源正在同步中，请稍后再试");
        }
        try {
            return doSync(source);
        } finally {
            syncing.remove(sourceId);
        }
    }

    @Override
    public List<OfficialFeedSyncResult> syncAllEnabled() {
        List<OfficialFeedSourceEntity> sources = sourceMapper.selectList(new LambdaQueryWrapper<OfficialFeedSourceEntity>()
                .eq(OfficialFeedSourceEntity::getCampusId, campusContextService.currentCampusId())
                .eq(OfficialFeedSourceEntity::getEnabled, true)
                .orderByAsc(OfficialFeedSourceEntity::getId));
        List<OfficialFeedSyncResult> results = new ArrayList<>();
        for (OfficialFeedSourceEntity source : sources) {
            try {
                results.add(sync(source.getId()));
            } catch (Exception exception) {
                log.warn("官方通知同步失败, sourceId={}, reason={}", source.getId(), exception.getMessage());
                results.add(new OfficialFeedSyncResult(source.getId(), source.getSourceName(), 0, 0, 0, 1,
                        "FAILED", List.of(new OfficialFeedSyncResult.Item(null, null, null, null, "FAILED",
                        truncate(exception.getMessage(), 300)))));
            }
        }
        return results;
    }

    @Override
    public PageResult<AdminOfficialFeedArticleResponse> pageArticles(int pageNum, int pageSize, Long sourceId,
                                                                     String status) {
        int current = Math.max(1, pageNum);
        int size = Math.max(1, Math.min(pageSize, 100));
        Page<OfficialFeedArticleEntity> page = articleMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<OfficialFeedArticleEntity>()
                        .eq(sourceId != null, OfficialFeedArticleEntity::getSourceId, sourceId)
                        .inSql(OfficialFeedArticleEntity::getSourceId,
                                "SELECT id FROM official_feed_source WHERE deleted = 0 AND campus_id = "
                                        + campusContextService.currentCampusId())
                        .eq(StringUtils.hasText(status), OfficialFeedArticleEntity::getStatus, status)
                        .orderByDesc(OfficialFeedArticleEntity::getFetchedAt));
        Map<Long, String> sourceNames = sourceMapper.selectList(null).stream()
                .collect(Collectors.toMap(OfficialFeedSourceEntity::getId, OfficialFeedSourceEntity::getSourceName,
                        (a, b) -> a));
        List<AdminOfficialFeedArticleResponse> records = page.getRecords().stream()
                .map(article -> new AdminOfficialFeedArticleResponse(article.getId(), article.getSourceId(),
                        sourceNames.get(article.getSourceId()), article.getTitle(), article.getSourceUrl(),
                        article.getPublishedAt(), article.getPostId(), article.getPostType(), article.getImageCount(),
                        article.getStatus(),
                        article.getImportMode() == null
                                ? OfficialFeedArticleEntity.MODE_CRAWL : article.getImportMode(),
                        Boolean.TRUE.equals(article.getCopyrightConfirmed()),
                        article.getScheduledAt(),
                        article.getRetryCount() == null ? 0 : article.getRetryCount(),
                        StringUtils.hasText(article.getPlainText())
                                || StringUtils.hasText(article.getContentBlocks()),
                        article.getErrorMessage(), article.getFetchedAt()))
                .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    private OfficialFeedSyncResult doSync(OfficialFeedSourceEntity source) {
        LocalDateTime startedAt = LocalDateTime.now();
        List<String> urls = siteClient.discover(source.getListUrl(), source.getMaxPages() == null ? 1 : source.getMaxPages());
        Map<String, OfficialFeedArticleEntity> existing = articleMapper.selectList(
                        new LambdaQueryWrapper<OfficialFeedArticleEntity>()
                                .eq(OfficialFeedArticleEntity::getSourceId, source.getId()))
                .stream().collect(Collectors.toMap(OfficialFeedArticleEntity::getSourceUrl, Function.identity(),
                        (a, b) -> a));
        int published = 0;
        int skipped = 0;
        int failed = 0;
        int processed = 0;
        int maxArticles = source.getMaxArticles() == null ? 3 : source.getMaxArticles();
        int maxAgeDays = source.getMaxAgeDays() == null ? 120 : source.getMaxAgeDays();
        List<OfficialFeedSyncResult.Item> items = new ArrayList<>();

        for (String url : urls) {
            if (processed >= maxArticles) {
                break;
            }
            OfficialFeedArticleEntity record = existing.get(url);
            if (record != null && !OfficialFeedArticleEntity.STATUS_FAILED.equals(record.getStatus())) {
                continue;
            }
            processed++;
            try {
                OfficialSiteClient.OfficialArticle article = siteClient.fetchArticle(url);
                if (article == null || article.blocks().isEmpty()) {
                    failed++;
                    saveArticle(record, source, url, url, null, null, null, 0,
                            OfficialFeedArticleEntity.STATUS_FAILED, "正文为空或无法解析");
                    items.add(new OfficialFeedSyncResult.Item(url, url, null, null, "FAILED", "正文为空或无法解析"));
                    continue;
                }
                String contentHash = sha256(article.title() + "\n" + article.plainText());
                if (article.publishedAt() != null
                        && article.publishedAt().isBefore(startedAt.minusDays(maxAgeDays))) {
                    skipped++;
                    saveArticle(record, source, url, article.title(), article.publishedAt(), contentHash, null, 0,
                            OfficialFeedArticleEntity.STATUS_SKIPPED, "超过 " + maxAgeDays + " 天，未发布");
                    items.add(new OfficialFeedSyncResult.Item(article.title(), url, null, null, "SKIPPED",
                            "超过 " + maxAgeDays + " 天"));
                    continue;
                }
                OfficialFeedArticleEntity duplicate = articleMapper.selectOne(
                        new LambdaQueryWrapper<OfficialFeedArticleEntity>()
                                .eq(OfficialFeedArticleEntity::getContentHash, contentHash)
                                .last("LIMIT 1"));
                if (duplicate != null && duplicate.getPostId() != null) {
                    skipped++;
                    saveArticle(record, source, url, article.title(), article.publishedAt(), contentHash, null, 0,
                            OfficialFeedArticleEntity.STATUS_SKIPPED, "内容重复，已有帖子 " + duplicate.getPostId());
                    items.add(new OfficialFeedSyncResult.Item(article.title(), url, duplicate.getPostId(),
                            duplicate.getPostType(), "SKIPPED", "内容重复"));
                    continue;
                }

                PreparedArticle prepared = prepare(source, article, url);
                CommunityPostEntity post = publish(source, article, prepared);
                published++;
                saveArticle(record, source, url, article.title(), article.publishedAt(), contentHash, post.getId(),
                        prepared.imageIds.size(), OfficialFeedArticleEntity.STATUS_PUBLISHED, null);
                items.add(new OfficialFeedSyncResult.Item(article.title(), url, post.getId(), post.getPostType(),
                        "PUBLISHED", null));
            } catch (Exception exception) {
                failed++;
                log.warn("官方通知处理失败, sourceId={}, url={}, reason={}", source.getId(), url, exception.getMessage());
                saveArticle(record, source, url, url, null, null, null, 0,
                        OfficialFeedArticleEntity.STATUS_FAILED, truncate(exception.getMessage(), 500));
                items.add(new OfficialFeedSyncResult.Item(url, url, null, null, "FAILED",
                        truncate(exception.getMessage(), 200)));
            }
        }

        String status = "发现 " + urls.size() + " 篇，发布 " + published + "，跳过 " + skipped + "，失败 " + failed;
        OfficialFeedSourceEntity update = new OfficialFeedSourceEntity();
        update.setId(source.getId());
        update.setLastFetchAt(LocalDateTime.now());
        update.setLastStatus(truncate(status, 255));
        update.setVersion(source.getVersion());
        sourceMapper.updateById(update);
        log.info("官方通知同步完成, sourceId={}, {}", source.getId(), status);
        return new OfficialFeedSyncResult(source.getId(), source.getSourceName(), urls.size(), published, skipped,
                failed, status, items);
    }

    private PreparedArticle prepare(OfficialFeedSourceEntity source, OfficialSiteClient.OfficialArticle article,
                                    String sourceUrl) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        List<Long> imageIds = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int textLength = 0;
        for (OfficialSiteClient.OfficialBlock block : article.blocks()) {
            if (block.isText()) {
                if (textLength >= MAX_BLOCK_TEXT_LENGTH - 200 || plain.length() >= MAX_CONTENT_LENGTH) {
                    continue;
                }
                String text = block.text();
                int remain = MAX_BLOCK_TEXT_LENGTH - 200 - textLength;
                if (text.length() > remain) {
                    text = text.substring(0, remain);
                }
                textLength += text.length();
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("type", "TEXT");
                value.put("text", text);
                value.put("fileId", null);
                blocks.add(value);
                if (plain.length() < MAX_CONTENT_LENGTH) {
                    if (!plain.isEmpty()) {
                        plain.append("\n\n");
                    }
                    int room = MAX_CONTENT_LENGTH - plain.length();
                    plain.append(text.length() <= room ? text : text.substring(0, Math.max(0, room)));
                }
            } else if (block.isImage() && imageIds.size() < 9) {
                try {
                    OfficialSiteClient.DownloadedImage image = siteClient.fetchImage(block.imageUrl());
                    if (image == null || !SUPPORTED_IMAGE_TYPES.contains(image.contentType())) {
                        continue;
                    }
                    FileStorageService.StoredFile stored = fileStorageService.store(image.bytes(),
                            image.fileName(), image.contentType(), source.getAuthorId());
                    imageIds.add(stored.fileId());
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("type", "IMAGE");
                    value.put("text", null);
                    value.put("fileId", stored.fileId());
                    blocks.add(value);
                } catch (Exception exception) {
                    log.warn("官方通知图片入库失败, url={}, reason={}", block.imageUrl(), exception.getMessage());
                }
            }
        }
        String footer = "本文由学校官网通知自动同步，原文：" + sourceUrl;
        if (textLength + footer.length() < MAX_BLOCK_TEXT_LENGTH) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("type", "TEXT");
            value.put("text", footer);
            value.put("fileId", null);
            blocks.add(value);
        }
        return new PreparedArticle(blocks, imageIds, plain.toString());
    }

    private CommunityPostEntity publish(OfficialFeedSourceEntity source, OfficialSiteClient.OfficialArticle article,
                                        PreparedArticle prepared) throws Exception {
        boolean articleType = !prepared.imageIds.isEmpty();
        CommunityPostEntity post = new CommunityPostEntity();
        post.setCampusId(source.getCampusId());
        post.setSectionId(source.getSectionId());
        post.setAuthorId(source.getAuthorId());
        post.setTitle(truncate(article.title(), MAX_TITLE_LENGTH));
        post.setContent(truncate(prepared.plainText.isBlank() ? article.title() : prepared.plainText,
                MAX_CONTENT_LENGTH));
        post.setPostType(articleType ? "ARTICLE" : "DYNAMIC");
        post.setContentBlocks(articleType ? objectMapper.writeValueAsString(prepared.blocks) : null);
        post.setImageFileIds(JsonIds.write(prepared.imageIds));
        post.setTopics(null);
        post.setCommentCount(0);
        post.setLikeCount(0);
        post.setReportCount(0);
        post.setStatus("ENABLED");
        postMapper.insert(post);
        return post;
    }

    private void saveArticle(OfficialFeedArticleEntity existing, OfficialFeedSourceEntity source, String url,
                             String title, LocalDateTime publishedAt, String contentHash, Long postId,
                             int imageCount, String status, String errorMessage) {
        OfficialFeedArticleEntity entity = existing == null ? new OfficialFeedArticleEntity() : existing;
        entity.setSourceId(source.getId());
        entity.setSourceUrl(url);
        entity.setTitle(truncate(title == null || title.isBlank() ? url : title, 255));
        entity.setPublishedAt(publishedAt);
        entity.setContentHash(contentHash == null ? sha256(url) : contentHash);
        entity.setPostId(postId);
        entity.setPostType(postId == null ? null : (imageCount > 0 ? "ARTICLE" : "DYNAMIC"));
        entity.setImageCount(imageCount);
        entity.setStatus(status);
        entity.setErrorMessage(truncate(errorMessage, 500));
        entity.setFetchedAt(LocalDateTime.now());
        if (existing == null) {
            articleMapper.insert(entity);
        } else {
            articleMapper.updateById(entity);
        }
    }

    private void validateRequest(AdminOfficialFeedSourceRequest request) {
        siteClient.validateSourceUrl(request.listUrl().trim());
        CommunitySectionEntity section = sectionMapper.selectById(request.sectionId());
        if (section == null || !"ENABLED".equals(section.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "发布分区不存在或已停用");
        }
        UserEntity author = userMapper.selectById(request.authorId());
        if (author == null || !Boolean.TRUE.equals(author.getOfficial())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "发布主体必须是官方账号");
        }
        // 账号类型约束（V66）：官方抓取号必须为 OFFICIAL_AUTO，人工运营号/广告服务号不可作为抓取主体。
        if (!"OFFICIAL_AUTO".equals(author.getAccountType())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "官方抓取号必须使用 OFFICIAL_AUTO 账号发布");
        }
    }

    private void applyRequest(OfficialFeedSourceEntity entity, AdminOfficialFeedSourceRequest request) {
        entity.setSourceName(request.sourceName().trim());
        entity.setListUrl(request.listUrl().trim());
        entity.setSectionId(request.sectionId());
        entity.setAuthorId(request.authorId());
        entity.setMaxPages(request.maxPages() == null ? 1 : request.maxPages());
        entity.setMaxArticles(request.maxArticles() == null ? 3 : request.maxArticles());
        entity.setMaxAgeDays(request.maxAgeDays() == null ? 120 : request.maxAgeDays());
        entity.setEnabled(request.enabled() == null || request.enabled());
    }

    private OfficialFeedSourceEntity requireSource(Long sourceId) {
        OfficialFeedSourceEntity source = sourceMapper.selectById(sourceId);
        if (source == null || !campusContextService.currentCampusId().equals(source.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "抓取来源不存在或不属于当前校区");
        }
        return source;
    }

    private AdminOfficialFeedSourceResponse toResponse(OfficialFeedSourceEntity source,
                                                       CommunitySectionEntity section, UserEntity author) {
        return new AdminOfficialFeedSourceResponse(source.getId(), source.getSourceName(), source.getListUrl(),
                source.getSectionId(), section == null ? null : section.getSectionName(),
                source.getAuthorId(), author == null ? null : author.getDisplayName(),
                author == null ? null : author.getOfficialTitle(),
                source.getMaxPages(), source.getMaxArticles(), source.getMaxAgeDays(),
                Boolean.TRUE.equals(source.getEnabled()), source.getLastFetchAt(), source.getLastStatus());
    }

    // ------------------------------------------------------------------
    // C08：单篇 URL 导入 / 预览 / 编辑 / 定时 / 重试 / 版权确认
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOfficialFeedPreviewResponse importByUrl(AdminOfficialFeedImportRequest request) {
        String url = request.url().trim();
        siteClient.validateSourceUrl(url);
        OfficialFeedSourceEntity source = resolveSource(request.sourceId());
        OfficialFeedArticleEntity existing = articleMapper.selectOne(
                new LambdaQueryWrapper<OfficialFeedArticleEntity>()
                        .eq(OfficialFeedArticleEntity::getSourceUrl, url)
                        .last("LIMIT 1"));
        if (existing != null && !OfficialFeedArticleEntity.STATUS_FAILED.equals(existing.getStatus())) {
            return toPreview(existing, source);
        }
        OfficialSiteClient.OfficialArticle article = siteClient.fetchArticle(url);
        if (article == null || article.blocks().isEmpty()) {
            if (existing != null) {
                existing.setStatus(OfficialFeedArticleEntity.STATUS_FAILED);
                existing.setErrorMessage("正文为空或无法解析");
                articleMapper.updateById(existing);
            }
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "正文为空或无法解析，请确认链接是文章详情页");
        }
        PreparedArticle prepared = prepare(source, article, url);
        OfficialFeedArticleEntity entity = existing == null ? new OfficialFeedArticleEntity() : existing;
        entity.setSourceId(source.getId());
        entity.setSourceUrl(url);
        entity.setTitle(truncate(article.title(), 255));
        entity.setPublishedAt(article.publishedAt());
        entity.setContentHash(sha256(article.title() + "\n" + prepared.plainText()));
        entity.setPostId(null);
        entity.setPostType(null);
        entity.setImageCount(prepared.imageIds().size());
        entity.setStatus(OfficialFeedArticleEntity.STATUS_DRAFT);
        entity.setErrorMessage(null);
        entity.setFetchedAt(LocalDateTime.now());
        entity.setContentBlocks(writeJson(prepared.blocks()));
        entity.setPlainText(truncate(prepared.plainText(), MAX_CONTENT_LENGTH * 4));
        entity.setCoverUrl(null);
        entity.setImportMode(OfficialFeedArticleEntity.MODE_MANUAL_URL);
        entity.setCopyrightConfirmed(Boolean.TRUE.equals(request.copyrightConfirmed()));
        entity.setScheduledAt(null);
        entity.setRetryCount(0);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
        } else {
            entity.setUpdatedAt(LocalDateTime.now());
        }
        if (existing == null) {
            articleMapper.insert(entity);
        } else {
            articleMapper.updateById(entity);
        }
        log.info("官方通知单篇导入完成, articleId={}, url={}, images={}", entity.getId(), url,
                prepared.imageIds().size());
        return toPreview(entity, source);
    }

    @Override
    public AdminOfficialFeedPreviewResponse previewArticle(Long articleId) {
        OfficialFeedArticleEntity entity = requireArticle(articleId);
        return toPreview(entity, requireSource(entity.getSourceId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOfficialFeedPreviewResponse updateArticle(Long articleId,
                                                          AdminOfficialFeedArticleUpdateRequest request) {
        OfficialFeedArticleEntity entity = requireArticle(articleId);
        if (OfficialFeedArticleEntity.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "已发布文章不可编辑，请先下架处理");
        }
        List<Map<String, Object>> blocks = parseStoredBlocks(entity);
        if (StringUtils.hasText(request.plainText())) {
            List<Map<String, Object>> rebuilt = new ArrayList<>();
            Map<String, Object> textBlock = new LinkedHashMap<>();
            textBlock.put("type", "TEXT");
            textBlock.put("text", truncate(request.plainText().trim(), MAX_BLOCK_TEXT_LENGTH));
            textBlock.put("fileId", null);
            rebuilt.add(textBlock);
            for (Map<String, Object> block : blocks) {
                if ("IMAGE".equals(String.valueOf(block.get("type")).toUpperCase())) {
                    rebuilt.add(block);
                }
            }
            blocks = rebuilt;
            entity.setPlainText(truncate(request.plainText().trim(), MAX_CONTENT_LENGTH * 4));
        }
        entity.setTitle(truncate(request.title().trim(), 255));
        entity.setContentBlocks(writeJson(blocks));
        entity.setCoverUrl(StringUtils.hasText(request.coverUrl()) ? request.coverUrl().trim() : null);
        entity.setContentHash(sha256(entity.getTitle() + "\n"
                + (entity.getPlainText() == null ? "" : entity.getPlainText())));
        entity.setUpdatedAt(LocalDateTime.now());
        articleMapper.updateById(entity);
        return toPreview(entity, requireSource(entity.getSourceId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOfficialFeedPreviewResponse confirmCopyright(Long articleId, boolean confirmed) {
        OfficialFeedArticleEntity entity = requireArticle(articleId);
        OfficialFeedArticleEntity update = new OfficialFeedArticleEntity();
        update.setId(entity.getId());
        update.setCopyrightConfirmed(confirmed);
        articleMapper.updateById(update);
        entity.setCopyrightConfirmed(confirmed);
        return toPreview(entity, requireSource(entity.getSourceId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOfficialFeedPreviewResponse publishArticle(Long articleId) {
        OfficialFeedArticleEntity entity = requireArticle(articleId);
        publishStored(entity);
        return toPreview(entity, requireSource(entity.getSourceId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOfficialFeedPreviewResponse scheduleArticle(Long articleId, LocalDateTime scheduledAt) {
        OfficialFeedArticleEntity entity = requireArticle(articleId);
        if (scheduledAt == null || scheduledAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "定时发布时间必须晚于当前时间");
        }
        if (!Boolean.TRUE.equals(entity.getCopyrightConfirmed())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请先确认版权合规再定时发布");
        }
        if (OfficialFeedArticleEntity.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文章已发布");
        }
        OfficialFeedArticleEntity update = new OfficialFeedArticleEntity();
        update.setId(entity.getId());
        update.setStatus(OfficialFeedArticleEntity.STATUS_SCHEDULED);
        update.setScheduledAt(scheduledAt);
        articleMapper.updateById(update);
        entity.setStatus(OfficialFeedArticleEntity.STATUS_SCHEDULED);
        entity.setScheduledAt(scheduledAt);
        return toPreview(entity, requireSource(entity.getSourceId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOfficialFeedPreviewResponse retryArticle(Long articleId) {
        OfficialFeedArticleEntity entity = requireArticle(articleId);
        if (OfficialFeedArticleEntity.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "已发布文章无需重试");
        }
        OfficialFeedSourceEntity source = requireSource(entity.getSourceId());
        int retryCount = (entity.getRetryCount() == null ? 0 : entity.getRetryCount()) + 1;
        try {
            OfficialSiteClient.OfficialArticle article = siteClient.fetchArticle(entity.getSourceUrl());
            if (article == null || article.blocks().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "正文为空或无法解析");
            }
            PreparedArticle prepared = prepare(source, article, entity.getSourceUrl());
            entity.setTitle(truncate(article.title(), 255));
            entity.setPublishedAt(article.publishedAt());
            entity.setContentBlocks(writeJson(prepared.blocks()));
            entity.setPlainText(truncate(prepared.plainText(), MAX_CONTENT_LENGTH * 4));
            entity.setImageCount(prepared.imageIds().size());
            entity.setContentHash(sha256(article.title() + "\n" + prepared.plainText()));
            entity.setStatus(OfficialFeedArticleEntity.STATUS_DRAFT);
            entity.setErrorMessage(null);
        } catch (Exception exception) {
            // 重试失败不伪造成功：保留 FAILED 状态与错误摘要，页面可继续重试
            entity.setStatus(OfficialFeedArticleEntity.STATUS_FAILED);
            entity.setErrorMessage(truncate(exception.getMessage(), 500));
            log.warn("官方通知单篇重试失败, articleId={}, reason={}", articleId, exception.getMessage());
        }
        entity.setRetryCount(retryCount);
        entity.setFetchedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        articleMapper.updateById(entity);
        return toPreview(entity, source);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publishDueScheduledArticles() {
        List<OfficialFeedArticleEntity> due = articleMapper.selectList(
                new LambdaQueryWrapper<OfficialFeedArticleEntity>()
                        .eq(OfficialFeedArticleEntity::getStatus, OfficialFeedArticleEntity.STATUS_SCHEDULED)
                        .isNotNull(OfficialFeedArticleEntity::getScheduledAt)
                        .le(OfficialFeedArticleEntity::getScheduledAt, LocalDateTime.now()));
        int published = 0;
        for (OfficialFeedArticleEntity entity : due) {
            try {
                publishStored(entity);
                published++;
            } catch (Exception exception) {
                log.warn("定时发布官方通知失败, articleId={}, reason={}", entity.getId(), exception.getMessage());
                OfficialFeedArticleEntity update = new OfficialFeedArticleEntity();
                update.setId(entity.getId());
                update.setStatus(OfficialFeedArticleEntity.STATUS_FAILED);
                update.setErrorMessage(truncate("定时发布失败：" + exception.getMessage(), 500));
                articleMapper.updateById(update);
            }
        }
        return published;
    }

    private OfficialFeedSourceEntity resolveSource(Long sourceId) {
        if (sourceId != null) {
            return requireSource(sourceId);
        }
        List<OfficialFeedSourceEntity> sources = sourceMapper.selectList(
                new LambdaQueryWrapper<OfficialFeedSourceEntity>()
                        .eq(OfficialFeedSourceEntity::getCampusId, campusContextService.currentCampusId())
                        .orderByDesc(OfficialFeedSourceEntity::getEnabled)
                        .orderByAsc(OfficialFeedSourceEntity::getId)
                        .last("LIMIT 1"));
        if (sources.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "尚未配置官方抓取来源，请先在「官方通知」新增来源");
        }
        return sources.get(0);
    }

    private OfficialFeedArticleEntity requireArticle(Long articleId) {
        OfficialFeedArticleEntity entity = articleMapper.selectById(articleId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "官方通知记录不存在");
        }
        OfficialFeedSourceEntity source = sourceMapper.selectById(entity.getSourceId());
        if (source == null || !campusContextService.currentCampusId().equals(source.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "官方通知记录不存在或不属于当前校区");
        }
        return entity;
    }

    /** 发布已存草稿：校验版权/正文，复用社区发帖字段生成文章或动态 */
    private void publishStored(OfficialFeedArticleEntity entity) {
        if (OfficialFeedArticleEntity.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文章已发布，请勿重复发布");
        }
        if (!Boolean.TRUE.equals(entity.getCopyrightConfirmed())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请先确认版权合规再发布");
        }
        List<Map<String, Object>> blocks = parseStoredBlocks(entity);
        List<Long> imageIds = new ArrayList<>();
        for (Map<String, Object> block : blocks) {
            if ("IMAGE".equals(String.valueOf(block.get("type")).toUpperCase())
                    && block.get("fileId") instanceof Number number) {
                imageIds.add(number.longValue());
            }
        }
        String text = entity.getPlainText();
        if (!StringUtils.hasText(text) && blocks.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "正文为空，请先编辑或重试抓取");
        }
        OfficialFeedSourceEntity source = requireSource(entity.getSourceId());
        boolean articleType = !imageIds.isEmpty();
        CommunityPostEntity post = new CommunityPostEntity();
        post.setCampusId(source.getCampusId());
        post.setSectionId(source.getSectionId());
        post.setAuthorId(source.getAuthorId());
        post.setTitle(truncate(entity.getTitle(), MAX_TITLE_LENGTH));
        post.setContent(truncate(StringUtils.hasText(text) ? text : entity.getTitle(), MAX_CONTENT_LENGTH));
        post.setPostType(articleType ? "ARTICLE" : "DYNAMIC");
        post.setContentBlocks(articleType ? entity.getContentBlocks() : null);
        post.setImageFileIds(JsonIds.write(imageIds));
        post.setTopics(null);
        post.setCommentCount(0);
        post.setLikeCount(0);
        post.setReportCount(0);
        post.setStatus("ENABLED");
        postMapper.insert(post);
        entity.setPostId(post.getId());
        entity.setPostType(post.getPostType());
        entity.setStatus(OfficialFeedArticleEntity.STATUS_PUBLISHED);
        entity.setScheduledAt(null);
        entity.setPublishedAt(entity.getPublishedAt() == null ? LocalDateTime.now() : entity.getPublishedAt());
        entity.setFetchedAt(LocalDateTime.now());
        entity.setErrorMessage(null);
        articleMapper.updateById(entity);
        log.info("官方通知已发布, articleId={}, postId={}, postType={}", entity.getId(), post.getId(),
                post.getPostType());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseStoredBlocks(OfficialFeedArticleEntity entity) {
        if (!StringUtils.hasText(entity.getContentBlocks())) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(entity.getContentBlocks(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                    });
        } catch (Exception exception) {
            log.warn("官方通知图文块解析失败, articleId={}", entity.getId());
            return new ArrayList<>();
        }
    }

    private AdminOfficialFeedPreviewResponse toPreview(OfficialFeedArticleEntity entity,
                                                       OfficialFeedSourceEntity source) {
        List<Map<String, Object>> blocks = parseStoredBlocks(entity);
        List<Long> imageIds = new ArrayList<>();
        for (Map<String, Object> block : blocks) {
            if ("IMAGE".equals(String.valueOf(block.get("type")).toUpperCase())
                    && block.get("fileId") instanceof Number number && !imageIds.contains(number.longValue())) {
                imageIds.add(number.longValue());
            }
        }
        List<String> urls = imageIds.isEmpty() ? List.of() : fileStorageService.signViewPaths(imageIds);
        Map<Long, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < imageIds.size(); i++) {
            index.put(imageIds.get(i), i);
        }
        List<CommunityContentBlockResponse> previewBlocks = new ArrayList<>();
        for (Map<String, Object> block : blocks) {
            String type = String.valueOf(block.get("type")).toUpperCase();
            if ("IMAGE".equals(type) && block.get("fileId") instanceof Number number) {
                Integer position = index.get(number.longValue());
                String url = position == null || position >= urls.size() ? null : urls.get(position);
                previewBlocks.add(new CommunityContentBlockResponse("IMAGE", null, number.longValue(), url, url));
            } else if ("TEXT".equals(type)) {
                previewBlocks.add(new CommunityContentBlockResponse("TEXT",
                        block.get("text") == null ? null : String.valueOf(block.get("text")), null, null, null));
            }
        }
        String cover = StringUtils.hasText(entity.getCoverUrl()) ? entity.getCoverUrl()
                : (urls.isEmpty() ? null : urls.get(0));
        String plain = entity.getPlainText() == null ? "" : entity.getPlainText();
        return new AdminOfficialFeedPreviewResponse(entity.getId(), entity.getSourceId(),
                source == null ? null : source.getSourceName(), entity.getTitle(), entity.getSourceUrl(),
                entity.getPublishedAt(), previewBlocks, plain, plain.length(), cover,
                entity.getImageCount() == null ? 0 : entity.getImageCount(),
                entity.getImportMode() == null ? OfficialFeedArticleEntity.MODE_CRAWL : entity.getImportMode(),
                entity.getStatus(), Boolean.TRUE.equals(entity.getCopyrightConfirmed()), entity.getScheduledAt(),
                entity.getRetryCount() == null ? 0 : entity.getRetryCount(), entity.getErrorMessage(),
                entity.getPostId());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("内容哈希计算失败", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "图文块序列化失败");
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record PreparedArticle(List<Map<String, Object>> blocks, List<Long> imageIds, String plainText) {
    }
}
