package com.studenthub.modules.push.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.push.content.controller.dto.ContentCalendarRequest;
import com.studenthub.modules.push.content.controller.dto.ContentCalendarResponse;
import com.studenthub.modules.push.content.controller.dto.ContentPreviewResponse;
import com.studenthub.modules.push.content.controller.dto.ContentSlotRequest;
import com.studenthub.modules.push.content.controller.dto.ContentSlotResponse;
import com.studenthub.modules.push.content.entity.ContentCalendarEntity;
import com.studenthub.modules.push.content.entity.ContentSlotEntity;
import com.studenthub.modules.push.content.mapper.ContentCalendarMapper;
import com.studenthub.modules.push.content.mapper.ContentSlotMapper;
import com.studenthub.modules.push.content.service.ContentOpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内容运营实现：所有查询/写入按管理端校区上下文隔离。
 *
 * <p>预览与上线共用同一套校验（{@link #validateSlot}），保证「预览通过 = 可上线」，
 * 预览返回的冲突项是当前同位置真实在线内容，不是示例数据。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentOpsServiceImpl implements ContentOpsService {

    private static final Set<String> SLOT_TYPES = Set.of("BANNER", "POPUP", "RECOMMEND", "TOPIC", "ACTIVITY");
    private static final Set<String> POSITIONS = Set.of(
            "HOME_TOP", "HOME_POPUP", "SERVICE_HALL", "COMMUNITY_FEED", "PROFILE");
    private static final Set<String> TARGET_TYPES = Set.of("POST", "ACTIVITY", "TOPIC", "URL", "NONE");
    private static final Set<String> SLOT_STATUSES = Set.of("DRAFT", "ONLINE", "OFFLINE");
    private static final Set<String> ENTRY_TYPES = Set.of("ACTIVITY", "TOPIC", "RECOMMEND", "PROMOTION");
    private static final Set<String> CALENDAR_STATUSES = Set.of("PLANNED", "PUBLISHED", "OFFLINE");

    private final ContentSlotMapper slotMapper;
    private final ContentCalendarMapper calendarMapper;
    private final AdminCampusContextService campusContextService;

    // ---------------------------------------------------------------- 运营位

    @Override
    public List<ContentSlotResponse> listSlots(String slotType, String status, String position, String keyword) {
        String type = normalizeEnum(slotType);
        String state = normalizeEnum(status);
        LambdaQueryWrapper<ContentSlotEntity> wrapper = new LambdaQueryWrapper<ContentSlotEntity>()
                .eq(ContentSlotEntity::getCampusId, campusId())
                .eq(StringUtils.hasText(type), ContentSlotEntity::getSlotType, type)
                .eq(StringUtils.hasText(state), ContentSlotEntity::getStatus, state)
                .eq(StringUtils.hasText(position), ContentSlotEntity::getPosition, normalizeEnum(position))
                .and(StringUtils.hasText(keyword), w -> {
                    String value = keyword.trim();
                    w.like(ContentSlotEntity::getTitle, value)
                            .or().like(ContentSlotEntity::getSlotName, value)
                            .or().like(ContentSlotEntity::getSlotCode, value);
                })
                .orderByDesc(ContentSlotEntity::getWeight)
                .orderByAsc(ContentSlotEntity::getSortOrder)
                .orderByDesc(ContentSlotEntity::getId);
        return slotMapper.selectList(wrapper).stream().map(ContentSlotResponse::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentSlotResponse createSlot(ContentSlotRequest request) {
        Long campusId = campusId();
        String code = request.slotCode().trim().toUpperCase();
        Long exists = slotMapper.selectCount(new LambdaQueryWrapper<ContentSlotEntity>()
                .eq(ContentSlotEntity::getCampusId, campusId)
                .eq(ContentSlotEntity::getSlotCode, code));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "运营位编码已存在：" + code);
        }
        ContentSlotEntity entity = new ContentSlotEntity();
        entity.setCampusId(campusId);
        entity.setSlotCode(code);
        entity.setStatus("DRAFT");
        applySlot(entity, request);
        slotMapper.insert(entity);
        log.info("内容运营位已创建, campusId={}, code={}", campusId, code);
        return ContentSlotResponse.from(slotMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentSlotResponse updateSlot(Long id, ContentSlotRequest request) {
        ContentSlotEntity entity = requireSlot(id);
        String code = request.slotCode() == null ? null : request.slotCode().trim().toUpperCase();
        if (StringUtils.hasText(code) && !code.equals(entity.getSlotCode())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "运营位编码创建后不可修改");
        }
        applySlot(entity, request);
        slotMapper.updateById(entity);
        return ContentSlotResponse.from(slotMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSlot(Long id) {
        ContentSlotEntity entity = requireSlot(id);
        if ("ONLINE".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "在线中的运营位请先下线再删除");
        }
        Long bound = calendarMapper.selectCount(new LambdaQueryWrapper<ContentCalendarEntity>()
                .eq(ContentCalendarEntity::getCampusId, entity.getCampusId())
                .eq(ContentCalendarEntity::getSlotId, entity.getId())
                .ne(ContentCalendarEntity::getStatus, "OFFLINE"));
        if (bound != null && bound > 0) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID,
                    "仍有 " + bound + " 条排期引用该运营位，请先解除绑定");
        }
        slotMapper.deleteById(entity.getId());
        log.info("内容运营位已删除, id={}, code={}", entity.getId(), entity.getSlotCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentSlotResponse onlineSlot(Long id) {
        ContentSlotEntity entity = requireSlot(id);
        List<String> warnings = new ArrayList<>();
        if (!validateSlot(entity, warnings)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, String.join("；", warnings));
        }
        entity.setStatus("ONLINE");
        slotMapper.updateById(entity);
        log.info("内容运营位已上线, id={}, code={}", entity.getId(), entity.getSlotCode());
        return ContentSlotResponse.from(slotMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentSlotResponse offlineSlot(Long id) {
        ContentSlotEntity entity = requireSlot(id);
        entity.setStatus("OFFLINE");
        slotMapper.updateById(entity);
        return ContentSlotResponse.from(slotMapper.selectById(id));
    }

    @Override
    public ContentPreviewResponse previewSlot(Long id) {
        ContentSlotEntity entity = requireSlot(id);
        List<String> warnings = new ArrayList<>();
        boolean valid = validateSlot(entity, warnings);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("slotCode", entity.getSlotCode());
        payload.put("slotName", entity.getSlotName());
        payload.put("slotType", entity.getSlotType());
        payload.put("position", entity.getPosition());
        payload.put("title", entity.getTitle());
        payload.put("subtitle", entity.getSubtitle());
        payload.put("imageUrl", entity.getImageUrl());
        payload.put("target", targetLabel(entity.getTargetType(), entity.getTargetValue()));
        payload.put("startAt", entity.getStartAt());
        payload.put("endAt", entity.getEndAt());
        payload.put("status", entity.getStatus());
        payload.put("runtimeState", ContentSlotResponse.runtimeState(entity));
        return new ContentPreviewResponse(valid, warnings, conflictsOf(entity), payload);
    }

    // ---------------------------------------------------------------- 内容日历

    @Override
    public List<ContentCalendarResponse> listCalendar(LocalDateTime from, LocalDateTime to,
                                                      String status, String entryType) {
        String state = normalizeEnum(status);
        String type = normalizeEnum(entryType);
        LambdaQueryWrapper<ContentCalendarEntity> wrapper = new LambdaQueryWrapper<ContentCalendarEntity>()
                .eq(ContentCalendarEntity::getCampusId, campusId())
                .eq(StringUtils.hasText(state), ContentCalendarEntity::getStatus, state)
                .eq(StringUtils.hasText(type), ContentCalendarEntity::getEntryType, type)
                .le(to != null, ContentCalendarEntity::getStartAt, to)
                .ge(from != null, ContentCalendarEntity::getEndAt, from)
                .orderByAsc(ContentCalendarEntity::getStartAt)
                .orderByDesc(ContentCalendarEntity::getId);
        List<ContentCalendarEntity> entries = calendarMapper.selectList(wrapper);
        Map<Long, String> slotNames = slotNames(entries);
        return entries.stream()
                .map(entry -> ContentCalendarResponse.from(entry, slotNames.get(entry.getSlotId())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentCalendarResponse createCalendar(ContentCalendarRequest request) {
        ContentCalendarEntity entity = new ContentCalendarEntity();
        entity.setCampusId(campusId());
        entity.setStatus("PLANNED");
        applyCalendar(entity, request);
        calendarMapper.insert(entity);
        log.info("内容日历排期已创建, campusId={}, title={}", entity.getCampusId(), entity.getTitle());
        return toCalendarResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentCalendarResponse updateCalendar(Long id, ContentCalendarRequest request) {
        ContentCalendarEntity entity = requireCalendar(id);
        if (!"PLANNED".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "仅已排期（未上线）的条目可编辑");
        }
        applyCalendar(entity, request);
        calendarMapper.updateById(entity);
        return toCalendarResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCalendar(Long id) {
        ContentCalendarEntity entity = requireCalendar(id);
        if ("PUBLISHED".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "已上线排期请先下线再删除");
        }
        calendarMapper.deleteById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentCalendarResponse publishCalendar(Long id) {
        ContentCalendarEntity entity = requireCalendar(id);
        List<String> warnings = new ArrayList<>();
        if (!validateCalendar(entity, warnings)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, String.join("；", warnings));
        }
        entity.setStatus("PUBLISHED");
        calendarMapper.updateById(entity);
        return toCalendarResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentCalendarResponse offlineCalendar(Long id) {
        ContentCalendarEntity entity = requireCalendar(id);
        entity.setStatus("OFFLINE");
        calendarMapper.updateById(entity);
        return toCalendarResponse(entity);
    }

    @Override
    public ContentPreviewResponse previewCalendar(Long id) {
        ContentCalendarEntity entity = requireCalendar(id);
        List<String> warnings = new ArrayList<>();
        boolean valid = validateCalendar(entity, warnings);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entryType", entity.getEntryType());
        payload.put("title", entity.getTitle());
        payload.put("summary", entity.getSummary());
        payload.put("coverUrl", entity.getCoverUrl());
        payload.put("startAt", entity.getStartAt());
        payload.put("endAt", entity.getEndAt());
        payload.put("status", entity.getStatus());
        payload.put("runtimeState", ContentCalendarResponse.runtimeState(entity));
        payload.put("slotId", entity.getSlotId());
        String slotName = entity.getSlotId() == null ? null
                : slotNames(List.of(entity)).get(entity.getSlotId());
        payload.put("slotName", slotName);
        List<String> conflicts = new ArrayList<>();
        if (entity.getSlotId() != null) {
            List<ContentCalendarEntity> overlapping = calendarMapper.selectList(
                    new LambdaQueryWrapper<ContentCalendarEntity>()
                            .eq(ContentCalendarEntity::getCampusId, entity.getCampusId())
                            .eq(ContentCalendarEntity::getSlotId, entity.getSlotId())
                            .eq(ContentCalendarEntity::getStatus, "PUBLISHED")
                            .ne(ContentCalendarEntity::getId, entity.getId()));
            for (ContentCalendarEntity other : overlapping) {
                if (overlaps(entity, other)) {
                    conflicts.add(other.getTitle());
                }
            }
        }
        return new ContentPreviewResponse(valid, warnings, conflicts, payload);
    }

    // ---------------------------------------------------------------- 校验与辅助

    private boolean validateSlot(ContentSlotEntity entity, List<String> warnings) {
        boolean valid = true;
        if (entity.getSlotType() == null || !SLOT_TYPES.contains(entity.getSlotType())) {
            warnings.add("运营位类型不合法");
            valid = false;
        }
        if (entity.getPosition() == null || !POSITIONS.contains(entity.getPosition())) {
            warnings.add("展示位置不合法");
            valid = false;
        }
        if (entity.getTargetType() == null || !TARGET_TYPES.contains(entity.getTargetType())) {
            warnings.add("跳转类型不合法");
            valid = false;
        }
        if (("BANNER".equals(entity.getSlotType()) || "POPUP".equals(entity.getSlotType()))
                && !StringUtils.hasText(entity.getImageUrl())) {
            warnings.add("BANNER/弹窗类型必须配置图片");
            valid = false;
        }
        if ("URL".equals(entity.getTargetType())
                && (!StringUtils.hasText(entity.getTargetValue())
                || !entity.getTargetValue().startsWith("http"))) {
            warnings.add("跳转类型为 URL 时必须填写 http(s) 地址");
            valid = false;
        }
        if (entity.getStartAt() != null && entity.getEndAt() != null
                && !entity.getEndAt().isAfter(entity.getStartAt())) {
            warnings.add("结束时间必须晚于开始时间");
            valid = false;
        }
        if (entity.getEndAt() != null && entity.getEndAt().isBefore(LocalDateTime.now())) {
            warnings.add("结束时间已过期，上线后不会展示");
        }
        return valid;
    }

    private List<String> conflictsOf(ContentSlotEntity entity) {
        List<ContentSlotEntity> online = slotMapper.selectList(new LambdaQueryWrapper<ContentSlotEntity>()
                .eq(ContentSlotEntity::getCampusId, entity.getCampusId())
                .eq(ContentSlotEntity::getPosition, entity.getPosition())
                .eq(ContentSlotEntity::getStatus, "ONLINE")
                .ne(ContentSlotEntity::getId, entity.getId()));
        List<String> conflicts = new ArrayList<>();
        for (ContentSlotEntity other : online) {
            if (overlaps(entity, other)) {
                conflicts.add(other.getSlotCode() + " · " + other.getTitle());
            }
        }
        return conflicts;
    }

    private boolean overlaps(ContentSlotEntity left, ContentSlotEntity right) {
        LocalDateTime leftStart = left.getStartAt();
        LocalDateTime leftEnd = left.getEndAt();
        LocalDateTime rightStart = right.getStartAt();
        LocalDateTime rightEnd = right.getEndAt();
        boolean leftEndOk = leftEnd == null || rightStart == null || !rightStart.isAfter(leftEnd);
        boolean rightEndOk = rightEnd == null || leftStart == null || !leftStart.isAfter(rightEnd);
        return leftEndOk && rightEndOk;
    }

    private boolean overlaps(ContentCalendarEntity left, ContentCalendarEntity right) {
        return !left.getStartAt().isAfter(right.getEndAt()) && !right.getStartAt().isAfter(left.getEndAt());
    }

    private boolean validateCalendar(ContentCalendarEntity entity, List<String> warnings) {
        boolean valid = true;
        if (entity.getEntryType() == null || !ENTRY_TYPES.contains(entity.getEntryType())) {
            warnings.add("排期类型不合法");
            valid = false;
        }
        if (entity.getStartAt() == null || entity.getEndAt() == null) {
            warnings.add("排期必须填写开始与结束时间");
            valid = false;
        } else if (!entity.getEndAt().isAfter(entity.getStartAt())) {
            warnings.add("结束时间必须晚于开始时间");
            valid = false;
        }
        if (entity.getSlotId() != null) {
            ContentSlotEntity slot = slotMapper.selectById(entity.getSlotId());
            if (slot == null || !slot.getCampusId().equals(entity.getCampusId())) {
                warnings.add("绑定的运营位不存在");
                valid = false;
            } else if (!"ONLINE".equals(slot.getStatus())) {
                warnings.add("绑定的运营位尚未上线，排期上线后不会展示");
            }
        }
        if (!StringUtils.hasText(entity.getCoverUrl()) && !"PROMOTION".equals(entity.getEntryType())) {
            warnings.add("未配置封面，活动/专题卡片将无图");
        }
        return valid;
    }

    private void applySlot(ContentSlotEntity entity, ContentSlotRequest request) {
        entity.setSlotName(request.slotName().trim());
        entity.setSlotType(normalizeEnum(request.slotType()));
        entity.setPosition(normalizeEnum(request.position()));
        entity.setTitle(request.title());
        entity.setSubtitle(request.subtitle());
        entity.setImageUrl(request.imageUrl());
        entity.setTargetType(normalizeEnum(request.targetType()));
        entity.setTargetValue(request.targetValue());
        entity.setWeight(request.weight() == null ? 0 : request.weight());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setStartAt(request.startAt());
        entity.setEndAt(request.endAt());
        entity.setRemark(request.remark());
    }

    private void applyCalendar(ContentCalendarEntity entity, ContentCalendarRequest request) {
        if (request.slotId() != null) {
            ContentSlotEntity slot = slotMapper.selectById(request.slotId());
            if (slot == null || !campusId().equals(slot.getCampusId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "绑定的运营位不存在");
            }
        }
        entity.setSlotId(request.slotId());
        entity.setEntryType(normalizeEnum(request.entryType()));
        entity.setTitle(request.title());
        entity.setSummary(request.summary());
        entity.setCoverUrl(request.coverUrl());
        entity.setStartAt(request.startAt());
        entity.setEndAt(request.endAt());
        entity.setOwner(request.owner());
        entity.setRemark(request.remark());
    }

    private ContentCalendarResponse toCalendarResponse(ContentCalendarEntity entity) {
        String slotName = entity.getSlotId() == null ? null : slotNames(List.of(entity)).get(entity.getSlotId());
        return ContentCalendarResponse.from(calendarMapper.selectById(entity.getId()), slotName);
    }

    private Map<Long, String> slotNames(List<ContentCalendarEntity> entries) {
        List<Long> slotIds = entries.stream().map(ContentCalendarEntity::getSlotId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (slotIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (ContentSlotEntity slot : slotMapper.selectBatchIds(slotIds)) {
            names.put(slot.getId(), slot.getSlotName());
        }
        return names;
    }

    private String targetLabel(String targetType, String targetValue) {
        if (targetType == null) {
            return null;
        }
        if ("NONE".equals(targetType)) {
            return "不跳转";
        }
        return targetType + (StringUtils.hasText(targetValue) ? ":" + targetValue : "");
    }

    private ContentSlotEntity requireSlot(Long id) {
        ContentSlotEntity entity = id == null ? null : slotMapper.selectById(id);
        if (entity == null || !campusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "运营位不存在");
        }
        return entity;
    }

    private ContentCalendarEntity requireCalendar(Long id) {
        ContentCalendarEntity entity = id == null ? null : calendarMapper.selectById(id);
        if (entity == null || !campusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "排期不存在");
        }
        return entity;
    }

    private Long campusId() {
        return campusContextService.currentCampusId();
    }

    private String normalizeEnum(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    @SuppressWarnings("unused")
    private boolean knownSlotStatus(String status) {
        return SLOT_STATUSES.contains(status);
    }

    @SuppressWarnings("unused")
    private boolean knownCalendarStatus(String status) {
        return CALENDAR_STATUSES.contains(status);
    }
}
