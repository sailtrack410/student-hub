package com.studenthub.modules.dict.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.achievement.entity.AchievementBadgeEntity;
import com.studenthub.modules.achievement.entity.AchievementCategoryEntity;
import com.studenthub.modules.achievement.mapper.AchievementBadgeMapper;
import com.studenthub.modules.achievement.mapper.AchievementCategoryMapper;
import com.studenthub.modules.dict.controller.dto.DictItemResponse;
import com.studenthub.modules.dict.controller.dto.DictTypeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成就/成长相关字典提供者：勋章分类、勋章目录、稀有度、指标、发放来源、成长等级。
 *
 * <p>勋章分类与勋章目录直接读取 achievement_category / achievement_badge 实时数据，
 * 在「字典管理」中作为只读字典展示；实际编辑仍在「成就管理」。等级名称与经验阈值来自
 * AchievementService 的同一套曲线，避免管理端展示与 App 计算不一致。</p>
 */
@Component
@RequiredArgsConstructor
public class AchievementDictProvider implements SystemDictProvider {

    public static final String SOURCE_ACHIEVEMENT = "ACHIEVEMENT";

    public static final String TYPE_CATEGORY = "ACHIEVEMENT_CATEGORY";
    public static final String TYPE_BADGE = "ACHIEVEMENT_BADGE";
    public static final String TYPE_RARITY = "ACHIEVEMENT_RARITY";
    public static final String TYPE_METRIC = "ACHIEVEMENT_METRIC";
    public static final String TYPE_GRANT_SOURCE = "ACHIEVEMENT_GRANT_SOURCE";
    public static final String TYPE_USER_LEVEL = "USER_LEVEL";

    private static final Map<String, String> RARITIES = new LinkedHashMap<>();
    private static final Map<String, String> METRICS = new LinkedHashMap<>();
    private static final Map<String, String> GRANT_SOURCES = new LinkedHashMap<>();
    private static final int[] LEVEL_EXP = {0, 50, 150, 350, 700, 1200, 2000, 3200, 5000};
    private static final String[] LEVEL_NAMES = {
            "初来乍到", "校园新人", "活跃同学", "热心同学", "校园骨干", "校园达人", "校园之星", "校园榜样", "校园传奇"};

    static {
        RARITIES.put("COMMON", "普通");
        RARITIES.put("RARE", "稀有");
        RARITIES.put("EPIC", "史诗");
        RARITIES.put("LEGENDARY", "传说");

        METRICS.put("POST_COUNT", "发帖数");
        METRICS.put("COMMENT_COUNT", "评论数");
        METRICS.put("POST_LIKES", "帖子累计获赞");
        METRICS.put("POST_LIKES_SINGLE", "单帖最高获赞");
        METRICS.put("REQUEST_COUNT", "服务申请数");
        METRICS.put("REQUEST_COMPLETED", "办结申请数");
        METRICS.put("EVALUATION_COUNT", "评价数");
        METRICS.put("EVALUATION_FIVE_STAR", "五星评价数");
        METRICS.put("WORKER_ORDER_COMPLETED", "完成工单数");
        METRICS.put("JW_AUTH", "教务认证");
        METRICS.put("SCHEDULE_IMPORTED", "课表导入");
        METRICS.put("PROFILE_COMPLETE", "资料完善");
        METRICS.put("LEVEL", "等级");

        GRANT_SOURCES.put("AUTO", "自动达成");
        GRANT_SOURCES.put("MANUAL", "管理端手动发放");
    }

    private final AchievementCategoryMapper categoryMapper;
    private final AchievementBadgeMapper badgeMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String typeCode) {
        return TYPE_CATEGORY.equals(typeCode) || TYPE_BADGE.equals(typeCode)
                || TYPE_RARITY.equals(typeCode) || TYPE_METRIC.equals(typeCode)
                || TYPE_GRANT_SOURCE.equals(typeCode) || TYPE_USER_LEVEL.equals(typeCode);
    }

    @Override
    public List<DictTypeResponse> listTypes() {
        List<DictTypeResponse> result = new ArrayList<>();
        result.add(type(TYPE_CATEGORY, "勋章分类", "成就模块维护；展示勋章所属分类与排序",
                categoryMapper.selectCount(null)));
        result.add(type(TYPE_BADGE, "勋章目录", "成就模块维护；含类别、稀有度、解锁指标与奖励经验",
                badgeMapper.selectCount(null)));
        result.add(type(TYPE_RARITY, "勋章稀有度", "成就模块维护；勋章稀有度枚举", (long) RARITIES.size()));
        result.add(type(TYPE_METRIC, "勋章指标", "成就模块维护；勋章解锁条件使用的成长指标",
                (long) METRICS.size()));
        result.add(type(TYPE_GRANT_SOURCE, "勋章发放来源", "成就模块维护；自动达成 / 手动发放",
                (long) GRANT_SOURCES.size()));
        result.add(type(TYPE_USER_LEVEL, "成长等级", "成就模块维护；1-9 级名称与累计经验阈值",
                (long) LEVEL_NAMES.length));
        return result;
    }

    @Override
    public List<DictItemResponse> listItems(String typeCode, boolean includeDisabled) {
        return switch (typeCode) {
            case TYPE_CATEGORY -> categoryItems(includeDisabled);
            case TYPE_BADGE -> badgeItems(includeDisabled);
            case TYPE_RARITY -> staticItems(TYPE_RARITY, RARITIES, -1300L);
            case TYPE_METRIC -> staticItems(TYPE_METRIC, METRICS, -1400L);
            case TYPE_GRANT_SOURCE -> staticItems(TYPE_GRANT_SOURCE, GRANT_SOURCES, -1500L);
            case TYPE_USER_LEVEL -> levelItems();
            default -> List.of();
        };
    }

    private DictTypeResponse type(String code, String name, String remark, Long count) {
        return new DictTypeResponse(null, code, name, remark, "ENABLED", true, count,
                null, null, SOURCE_ACHIEVEMENT, true);
    }

    private List<DictItemResponse> categoryItems(boolean includeDisabled) {
        LambdaQueryWrapper<AchievementCategoryEntity> wrapper = new LambdaQueryWrapper<>();
        if (!includeDisabled) {
            wrapper.eq(AchievementCategoryEntity::getEnabled, true);
        }
        wrapper.orderByAsc(AchievementCategoryEntity::getSortOrder)
                .orderByAsc(AchievementCategoryEntity::getCategoryCode);
        List<DictItemResponse> result = new ArrayList<>();
        long id = -1100L;
        for (AchievementCategoryEntity category : categoryMapper.selectList(wrapper)) {
            result.add(new DictItemResponse(id--, TYPE_CATEGORY, category.getCategoryCode(),
                    category.getCategoryName(), category.getCategoryCode(),
                    category.getSortOrder() == null ? 0 : category.getSortOrder(),
                    Boolean.TRUE.equals(category.getEnabled()) ? "ENABLED" : "DISABLED",
                    null, "来源：achievement_category"));
        }
        return result;
    }

    private List<DictItemResponse> badgeItems(boolean includeDisabled) {
        LambdaQueryWrapper<AchievementBadgeEntity> wrapper = new LambdaQueryWrapper<>();
        if (!includeDisabled) {
            wrapper.eq(AchievementBadgeEntity::getEnabled, true);
        }
        wrapper.orderByAsc(AchievementBadgeEntity::getSortOrder)
                .orderByAsc(AchievementBadgeEntity::getBadgeCode);
        List<DictItemResponse> result = new ArrayList<>();
        long id = -1200L;
        for (AchievementBadgeEntity badge : badgeMapper.selectList(wrapper)) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("category", badge.getCategory());
            extra.put("rarity", badge.getRarity());
            extra.put("description", badge.getDescription());
            extra.put("metricCode", badge.getMetricCode());
            extra.put("metricThreshold", badge.getMetricThreshold());
            extra.put("rewardExp", badge.getRewardExp());
            extra.put("iconUrl", badge.getIconUrl());
            result.add(new DictItemResponse(id--, TYPE_BADGE, badge.getBadgeCode(), badge.getBadgeName(),
                    badge.getBadgeCode(), badge.getSortOrder() == null ? 0 : badge.getSortOrder(),
                    Boolean.TRUE.equals(badge.getEnabled()) ? "ENABLED" : "DISABLED",
                    toJson(extra), "来源：achievement_badge；实际以「成就管理」为准"));
        }
        return result;
    }

    private List<DictItemResponse> staticItems(String typeCode, Map<String, String> values, long idBase) {
        List<DictItemResponse> result = new ArrayList<>();
        long id = idBase;
        int sort = 10;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result.add(new DictItemResponse(id--, typeCode, entry.getKey(), entry.getValue(), entry.getKey(),
                    sort, "ENABLED", null, null));
            sort += 10;
        }
        return result;
    }

    private List<DictItemResponse> levelItems() {
        List<DictItemResponse> result = new ArrayList<>();
        long id = -1600L;
        for (int i = 0; i < LEVEL_NAMES.length; i++) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("level", i + 1);
            extra.put("exp", LEVEL_EXP[i]);
            result.add(new DictItemResponse(id--, TYPE_USER_LEVEL, String.valueOf(i + 1),
                    LEVEL_NAMES[i], String.valueOf(i + 1), (i + 1) * 10, "ENABLED",
                    toJson(extra), "来源：AchievementService 等级曲线"));
        }
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
