package com.studenthub.modules.aichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.aichat.controller.dto.AiCostOverviewResponse;
import com.studenthub.modules.aichat.controller.dto.AiModelPricingRequest;
import com.studenthub.modules.aichat.entity.AiModelEntity;
import com.studenthub.modules.aichat.entity.AiProviderEntity;
import com.studenthub.modules.aichat.mapper.AiMessageMapper;
import com.studenthub.modules.aichat.mapper.AiModelMapper;
import com.studenthub.modules.aichat.mapper.AiProviderMapper;
import com.studenthub.modules.aichat.service.AiCostService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 成本看板实现。
 *
 * <p>计算口径：ai_message 中 role=ASSISTANT 的消息按 ai_message.model = ai_model.provider_model_id 关联；
 * prompt/completion tokens × 每 1K 单价 ÷ 1000 得到估算成本。任一使用中的模型未配置单价时，
 * 总成本与当日成本返回 null，并置 pricingConfigured=false；未登记到 ai_model 的消息模型也按未配置处理。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCostServiceImpl implements AiCostService {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final String PRICING_NOTE = "成本为按 ai_model 配置的每 1K tokens 单价估算的金额（币种以供应商定价为准，平台不做汇率换算）；"
            + "未配置单价或消息中的模型未在 AI 模型配置中登记时，成本返回 null，不推算金额。数据来源仅 ai_message 历史 token，不调用模型 API。";

    private final AiMessageMapper messageMapper;
    private final AiModelMapper modelMapper;
    private final AiProviderMapper providerMapper;
    private final AdminCampusContextService campusContextService;

    @Override
    public AiCostOverviewResponse overview(int days) {
        int rangeDays = days == 30 ? 30 : 7;
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(rangeDays - 1L);
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = end.plusDays(1).atStartOfDay();
        Long campusId = campusContextService.currentCampusId();
        if (campusId == null) {
            campusId = 0L;
        }

        List<AiModelEntity> models = modelMapper.selectList(new LambdaQueryWrapper<AiModelEntity>()
                .orderByDesc(AiModelEntity::getEnabled)
                .orderByAsc(AiModelEntity::getId));
        Map<Long, String> providerNames = providerNameMap();
        Map<String, AiModelEntity> modelByKey = modelKeyMap(models);

        List<Map<String, Object>> usageRows = messageMapper.aggregateCostByModel(campusId, startAt, endAt);
        List<AiCostOverviewResponse.ModelCost> byModel = new ArrayList<>();
        long totalRequests = 0;
        long totalPrompt = 0;
        long totalCompletion = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        int unpriced = 0;
        for (Map<String, Object> row : usageRows) {
            String modelKey = text(value(row, "model_key"));
            long requests = longValue(value(row, "requests"));
            long prompt = longValue(value(row, "prompt_tokens"));
            long completion = longValue(value(row, "completion_tokens"));
            AiModelEntity model = lookupModel(modelByKey, modelKey);
            BigDecimal inputPrice = model == null ? null : model.getInputPricePer1k();
            BigDecimal outputPrice = model == null ? null : model.getOutputPricePer1k();
            boolean configured = inputPrice != null && outputPrice != null;
            boolean costRelevant = prompt + completion > 0;
            BigDecimal cost;
            if (configured) {
                cost = estimate(prompt, completion, inputPrice, outputPrice);
                totalCost = totalCost.add(cost);
            } else if (costRelevant) {
                // 有 token 消耗但缺单价：成本未知，不能按 0 伪造
                cost = null;
                unpriced++;
            } else {
                // 无 token 的本地直答（如 model=system）：不参与成本，不影响 pricingConfigured
                cost = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
            }
            totalRequests += requests;
            totalPrompt += prompt;
            totalCompletion += completion;
            byModel.add(new AiCostOverviewResponse.ModelCost(
                    model == null ? null : model.getId(), modelKey,
                    model == null ? null : providerNames.get(model.getProviderId()),
                    model == null ? null : model.getModelType(),
                    requests, prompt, completion, prompt + completion,
                    inputPrice, outputPrice, configured, cost));
        }
        byModel.sort((left, right) -> Long.compare(right.requests(), left.requests()));

        boolean pricingConfigured = unpriced == 0;
        AiCostOverviewResponse.Kpis kpis = new AiCostOverviewResponse.Kpis(
                totalRequests, totalPrompt, totalCompletion, totalPrompt + totalCompletion,
                pricingConfigured ? totalCost.setScale(6, RoundingMode.HALF_UP) : null,
                pricingConfigured, unpriced);

        List<AiCostOverviewResponse.TrendPoint> trend = trend(campusId, start, end, startAt, endAt, modelByKey);
        List<AiCostOverviewResponse.ModelPricing> pricing = models.stream()
                .map(model -> toPricing(model, providerNames.get(model.getProviderId())))
                .toList();
        return new AiCostOverviewResponse(start.toString(), end.toString(), rangeDays, kpis, byModel, trend,
                pricing, PRICING_NOTE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiCostOverviewResponse.ModelPricing updatePricing(Long operatorId, Long modelId,
                                                             AiModelPricingRequest request) {
        AiModelEntity existing = modelMapper.selectById(modelId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "AI 模型不存在");
        }
        // 显式 set(null) 以支持清除单价（MyBatis-Plus 默认 update 忽略 null 字段）
        modelMapper.update(null, new LambdaUpdateWrapper<AiModelEntity>()
                .eq(AiModelEntity::getId, modelId)
                .set(AiModelEntity::getInputPricePer1k, request.inputPricePer1k())
                .set(AiModelEntity::getOutputPricePer1k, request.outputPricePer1k()));
        AiModelEntity updated = modelMapper.selectById(modelId);
        log.info("AI 模型单价已更新, modelId={}, operatorId={}, inputPriceSet={}, outputPriceSet={}",
                modelId, operatorId, request.inputPricePer1k() != null, request.outputPricePer1k() != null);
        return toPricing(updated, providerNameMap().get(updated.getProviderId()));
    }

    // ------------------------------------------------------------------
    // 聚合辅助
    // ------------------------------------------------------------------

    private List<AiCostOverviewResponse.TrendPoint> trend(Long campusId, LocalDate start, LocalDate end,
                                                          LocalDateTime startAt, LocalDateTime endAt,
                                                          Map<String, AiModelEntity> modelByKey) {
        Map<LocalDate, List<Map<String, Object>>> rowsByDay = new HashMap<>();
        for (Map<String, Object> row : messageMapper.aggregateCostByDayAndModel(campusId, startAt, endAt)) {
            LocalDate day = dateValue(value(row, "stat_date"));
            if (day == null) {
                continue;
            }
            rowsByDay.computeIfAbsent(day, key -> new ArrayList<>()).add(row);
        }
        List<AiCostOverviewResponse.TrendPoint> points = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            List<Map<String, Object>> rows = rowsByDay.getOrDefault(day, List.of());
            long requests = 0;
            long prompt = 0;
            long completion = 0;
            BigDecimal cost = BigDecimal.ZERO;
            boolean configured = true;
            for (Map<String, Object> row : rows) {
                long rowRequests = longValue(value(row, "requests"));
                long rowPrompt = longValue(value(row, "prompt_tokens"));
                long rowCompletion = longValue(value(row, "completion_tokens"));
                AiModelEntity model = lookupModel(modelByKey, text(value(row, "model_key")));
                BigDecimal inputPrice = model == null ? null : model.getInputPricePer1k();
                BigDecimal outputPrice = model == null ? null : model.getOutputPricePer1k();
                boolean rowCostRelevant = rowPrompt + rowCompletion > 0;
                if (inputPrice == null || outputPrice == null) {
                    if (rowCostRelevant) {
                        configured = false;
                    }
                } else {
                    cost = cost.add(estimate(rowPrompt, rowCompletion, inputPrice, outputPrice));
                }
                requests += rowRequests;
                prompt += rowPrompt;
                completion += rowCompletion;
            }
            points.add(new AiCostOverviewResponse.TrendPoint(day.toString(), requests, prompt, completion,
                    prompt + completion, configured ? cost.setScale(6, RoundingMode.HALF_UP) : null));
        }
        return points;
    }

    private Map<String, AiModelEntity> modelKeyMap(List<AiModelEntity> models) {
        Map<String, AiModelEntity> map = new HashMap<>();
        for (AiModelEntity model : models) {
            if (model.getProviderModelId() == null) {
                continue;
            }
            map.putIfAbsent(model.getProviderModelId(), model);
            map.putIfAbsent(model.getProviderModelId().toLowerCase(Locale.ROOT), model);
        }
        return map;
    }

    private AiModelEntity lookupModel(Map<String, AiModelEntity> modelByKey, String modelKey) {
        if (modelKey == null) {
            return null;
        }
        AiModelEntity exact = modelByKey.get(modelKey);
        return exact != null ? exact : modelByKey.get(modelKey.toLowerCase(Locale.ROOT));
    }

    private Map<Long, String> providerNameMap() {
        Map<Long, String> names = new HashMap<>();
        for (AiProviderEntity provider : providerMapper.selectList(null)) {
            names.put(provider.getId(), provider.getProviderName());
        }
        return names;
    }

    private AiCostOverviewResponse.ModelPricing toPricing(AiModelEntity model, String providerName) {
        boolean configured = model.getInputPricePer1k() != null && model.getOutputPricePer1k() != null;
        return new AiCostOverviewResponse.ModelPricing(model.getId(), providerName, model.getProviderModelId(),
                model.getModelType(), Boolean.TRUE.equals(model.getEnabled()),
                model.getInputPricePer1k(), model.getOutputPricePer1k(), configured);
    }

    private BigDecimal estimate(long promptTokens, long completionTokens, BigDecimal inputPrice,
                                BigDecimal outputPrice) {
        return inputPrice.multiply(BigDecimal.valueOf(promptTokens))
                .add(outputPrice.multiply(BigDecimal.valueOf(completionTokens)))
                .divide(THOUSAND, 6, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // 动态列（H2 返回大写列名）
    // ------------------------------------------------------------------

    private Object value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase(Locale.ROOT));
        }
        return value;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private LocalDate dateValue(Object value) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value).substring(0, 10));
        } catch (Exception exception) {
            return null;
        }
    }
}
