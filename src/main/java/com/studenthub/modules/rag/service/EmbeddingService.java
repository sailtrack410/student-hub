package com.studenthub.modules.rag.service;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.ai.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;

@Service @RequiredArgsConstructor
public class EmbeddingService {
    private final AiProvider provider;
    public EmbeddingResult embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    public List<EmbeddingResult> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        AiRuntimeSnapshot snapshot = AiRuntimeConfig.current();
        if (snapshot == null || snapshot.embedding() == null) throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED, "请先在 AI 配置中设置默认向量模型");
        AiEmbeddingBatchResponse response = provider.embedBatch(new AiEmbeddingBatchRequest(snapshot.embedding().model(), texts));
        if (response.vectors() == null || response.vectors().size() != texts.size()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "向量模型返回数量不匹配");
        }
        List<EmbeddingResult> result = new ArrayList<>(texts.size());
        for (List<Double> vector : response.vectors()) {
            if (vector == null || vector.isEmpty()) throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "向量模型返回为空");
            result.add(new EmbeddingResult(vector, response.model()));
        }
        return result;
    }
    public record EmbeddingResult(List<Double> vector, String model) {}
}
