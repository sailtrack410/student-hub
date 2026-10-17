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
import com.studenthub.modules.screen.entity.ScreenNoticeEntity;
import com.studenthub.modules.screen.mapper.ScreenNoticeMapper;
import com.studenthub.modules.screen.service.ScreenAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private final ScreenNoticeMapper noticeMapper;

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
