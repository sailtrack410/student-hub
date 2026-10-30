package com.studenthub.modules.screen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.screen.controller.dto.AdminNoticeResponse;
import com.studenthub.modules.screen.controller.dto.AppAnnouncementResponse;
import com.studenthub.modules.screen.controller.dto.NoticeUpsertRequest;
import com.studenthub.integration.storage.BinaryStorage;
import com.studenthub.modules.audit.aspect.AuditTrailContext;
import com.studenthub.modules.screen.entity.ScreenNoticeEntity;
import com.studenthub.modules.screen.mapper.ScreenNoticeMapper;
import com.studenthub.modules.screen.service.ScreenAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 公告管理实现：状态机 DRAFT/SCHEDULED→PUBLISHED→OFFLINE（可恢复 PUBLISHED）；
 * channel 校验白名单防脏数据；定时发布由 NoticePublishTimer 每 30 秒扫描 publishDueScheduled。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenAdminServiceImpl implements ScreenAdminService {

    private static final Set<String> CHANNELS = Set.of("SCREEN", "APP");
    private static final Set<String> STATUSES = Set.of("DRAFT", "SCHEDULED", "PUBLISHED", "OFFLINE");
    private static final Set<String> NOTICE_TYPES = Set.of("NOTICE", "NEWS", "ACTIVITY");
    private static final Map<String, String> TYPE_TEXT = Map.of(
            "NOTICE", "通知", "NEWS", "要闻", "ACTIVITY", "活动");

    private static final long MAX_COVER_BYTES = 2L * 1024L * 1024L;
    private static final String COVER_OBJECT_PREFIX = "static/assets/notices/";
    private static final String COVER_URL_PREFIX = "/api/common/assets/notices/";

    private final ScreenNoticeMapper noticeMapper;
    private final BinaryStorage binaryStorage;

    @Override
    public List<AdminNoticeResponse> list(String channel, String publishStatus, String noticeType) {
        List<ScreenNoticeEntity> list = noticeMapper.selectList(new LambdaQueryWrapper<ScreenNoticeEntity>()
                .eq(noticeMapperChannel(channel) != null, ScreenNoticeEntity::getChannel, noticeMapperChannel(channel))
                .eq(publishStatus != null && !publishStatus.isBlank(), ScreenNoticeEntity::getPublishStatus, publishStatus)
                .eq(noticeType != null && !noticeType.isBlank(), ScreenNoticeEntity::getNoticeType, noticeType)
                .orderByDesc(ScreenNoticeEntity::getPublishDate)
                .orderByDesc(ScreenNoticeEntity::getId));
        return list.stream().map(n -> AdminNoticeResponse.from(n, TYPE_TEXT.getOrDefault(n.getNoticeType(), "通知"))).toList();
    }

    private String noticeMapperChannel(String channel) {
        return channel == null || channel.isBlank() ? null : channel;
    }

    @Override
    public PageResult<AppAnnouncementResponse> pageAppAnnouncements(long pageNum, long pageSize) {
        long size = Math.min(Math.max(pageSize, 1), 50);
        Page<ScreenNoticeEntity> page = noticeMapper.selectPage(new Page<>(Math.max(pageNum, 1), size),
                new LambdaQueryWrapper<ScreenNoticeEntity>()
                        .eq(ScreenNoticeEntity::getChannel, "APP")
                        .eq(ScreenNoticeEntity::getPublishStatus, "PUBLISHED")
                        .orderByDesc(ScreenNoticeEntity::getPublishDate)
                        .orderByDesc(ScreenNoticeEntity::getId));
        List<AppAnnouncementResponse> records = page.getRecords().stream()
                .map(n -> AppAnnouncementResponse.from(n, TYPE_TEXT.getOrDefault(n.getNoticeType(), "通知")))
                .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminNoticeResponse create(NoticeUpsertRequest request, Long operatorId) {
        ScreenNoticeEntity entity = new ScreenNoticeEntity();
        applyUpsert(entity, request);
        String status = resolveInitialStatus(request);
        entity.setPublishStatus(status);
        applyPublishAt(entity, request, status);
        if (entity.getPublishDate() == null) {
            entity.setPublishDate(java.time.LocalDate.now());
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        noticeMapper.insert(entity);
        return AdminNoticeResponse.from(entity, TYPE_TEXT.getOrDefault(entity.getNoticeType(), "通知"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminNoticeResponse update(Long id, NoticeUpsertRequest request, Long operatorId) {
        ScreenNoticeEntity entity = requireNotice(id);
        // 审计变更前快照：标题/状态/正文长度/封面，便于追溯改稿
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("id", entity.getId());
        before.put("title", entity.getTitle());
        before.put("publishStatus", entity.getPublishStatus());
        before.put("channel", entity.getChannel());
        before.put("contentLength", entity.getContent() == null ? 0 : entity.getContent().length());
        before.put("coverImageUrl", entity.getCoverImageUrl());
        AuditTrailContext.setBefore(before);
        applyUpsert(entity, request);
        String status = resolveInitialStatus(request);
        entity.setPublishStatus(status);
        applyPublishAt(entity, request, status);
        noticeMapper.updateById(entity);
        return AdminNoticeResponse.from(entity, TYPE_TEXT.getOrDefault(entity.getNoticeType(), "通知"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminNoticeResponse changeStatus(Long id, String publishStatus) {
        if (!STATUSES.contains(publishStatus)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "非法发布状态");
        }
        ScreenNoticeEntity entity = requireNotice(id);
        entity.setPublishStatus(publishStatus);
        entity.setPublishAt("SCHEDULED".equals(publishStatus) ? entity.getPublishAt() : null);
        noticeMapper.updateById(entity);
        return AdminNoticeResponse.from(entity, TYPE_TEXT.getOrDefault(entity.getNoticeType(), "通知"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireNotice(id);
        noticeMapper.deleteById(id);
    }

    @Override
    public String uploadCover(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请选择封面图片");
        }
        if (file.getSize() > MAX_COVER_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "封面图片不能超过 2MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "封面图片读取失败");
        }
        String extension = detectImageExtension(content);
        if (extension == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "封面仅支持 PNG/JPEG/WebP 格式");
        }
        String objectKey = COVER_OBJECT_PREFIX + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String contentType = switch (extension) {
            case "png" -> org.springframework.http.MediaType.IMAGE_PNG_VALUE;
            case "jpg" -> org.springframework.http.MediaType.IMAGE_JPEG_VALUE;
            default -> "image/webp";
        };
        try {
            binaryStorage.put(objectKey, content, contentType);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "封面上传失败，请稍后重试");
        }
        return COVER_URL_PREFIX + objectKey.substring(COVER_OBJECT_PREFIX.length());
    }

    /** 内容嗅探：不信任文件名与 Content-Type，按魔数判断 PNG/JPEG/WebP */
    private String detectImageExtension(byte[] content) {
        if (content.length >= 8 && (content[0] & 0xFF) == 0x89 && content[1] == 0x50
                && content[2] == 0x4E && content[3] == 0x47) {
            return "png";
        }
        if (content.length >= 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (content.length >= 12 && content[0] == 'R' && content[1] == 'I' && content[2] == 'F'
                && content[3] == 'F' && content[8] == 'W' && content[9] == 'E' && content[10] == 'B'
                && content[11] == 'P') {
            return "webp";
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publishDueScheduled() {
        List<ScreenNoticeEntity> due = noticeMapper.selectList(new LambdaQueryWrapper<ScreenNoticeEntity>()
                .eq(ScreenNoticeEntity::getPublishStatus, "SCHEDULED")
                .isNotNull(ScreenNoticeEntity::getPublishAt)
                .le(ScreenNoticeEntity::getPublishAt, LocalDateTime.now()));
        for (ScreenNoticeEntity entity : due) {
            ScreenNoticeEntity update = new ScreenNoticeEntity();
            update.setId(entity.getId());
            update.setPublishStatus("PUBLISHED");
            update.setPublishDate(java.time.LocalDate.now());
            noticeMapper.updateById(update);
        }
        return due.size();
    }

    private ScreenNoticeEntity requireNotice(Long id) {
        ScreenNoticeEntity entity = noticeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        return entity;
    }

    private void applyUpsert(ScreenNoticeEntity entity, NoticeUpsertRequest request) {
        if (!CHANNELS.contains(request.channel())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "channel 仅支持 SCREEN/APP");
        }
        if (!NOTICE_TYPES.contains(request.noticeType())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "noticeType 仅支持 NOTICE/NEWS/ACTIVITY");
        }
        entity.setNoticeType(request.noticeType());
        entity.setTitle(request.title());
        entity.setSummary(request.summary());
        entity.setContent(request.content());
        entity.setCoverImageUrl(request.coverImageUrl());
        entity.setPublishDate(request.publishDate());
        entity.setChannel(request.channel());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setAuthor(request.author());
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
    }

    private String resolveInitialStatus(NoticeUpsertRequest request) {
        String action = request.action() == null ? "PUBLISH" : request.action().toUpperCase();
        return switch (action) {
            case "DRAFT" -> "DRAFT";
            case "OFFLINE" -> "OFFLINE";
            case "SCHEDULE" -> {
                if (request.publishAt() == null || request.publishAt().isBefore(LocalDateTime.now())) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "定时发布需提供未来的 publishAt");
                }
                yield "SCHEDULED";
            }
            case "PUBLISH" -> "PUBLISHED";
            default -> throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "action 仅支持 PUBLISH/SCHEDULE/DRAFT/OFFLINE");
        };
    }

    /** 定时时间只随 SCHEDULE 动作生效：否则清空，防止换动作后残留旧 publishAt 干扰扫描 */
    private void applyPublishAt(ScreenNoticeEntity entity, NoticeUpsertRequest request, String status) {
        entity.setPublishAt("SCHEDULED".equals(status) ? request.publishAt() : null);
    }
}
