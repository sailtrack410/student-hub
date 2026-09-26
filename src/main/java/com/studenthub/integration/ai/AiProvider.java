package com.studenthub.integration.ai;

import java.util.ArrayList;
import java.util.List;

public interface AiProvider {

    AiChatResponse chat(AiChatRequest request);

    /**
     * 流式对话（显式绑定）：按绑定里的模型与供应商配置调用。
     * 绑定不支持流式（streaming=false）时由实现回退为阻塞调用一次性回调；
     * 本接口默认实现即该回退本身（阻塞调用后一次性回调）。
     *
     * @param onDone  结束回调，参数依次为模型名、prompt tokens（可空）、completion tokens（可空）
     */
    default void chatStreamWithBinding(AiModelBinding binding, AiChatRequest request,
                                       java.util.function.Consumer<String> onDelta,
                                       TriConsumer<String, Integer, Integer> onDone) {
        AiChatResponse response = chat(request);
        onDelta.accept(response.content());
        onDone.accept(response.model(), response.promptTokens(), response.completionTokens());
    }

    /** 三参数无返回值回调（流式结束通知用） */
    interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }

    default AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        throw new UnsupportedOperationException("embedding not supported");
    }

    /**
     * Batch-compatible default for providers that only implement single input.
     * Providers with a native /embeddings batch endpoint should override this.
     */
    default AiEmbeddingBatchResponse embedBatch(AiEmbeddingBatchRequest request) {
        List<List<Double>> vectors = new ArrayList<>(request.input().size());
        Integer usage = null;
        for (String input : request.input()) {
            AiEmbeddingResponse response = embed(new AiEmbeddingRequest(request.model(), input));
            vectors.add(response.vector());
            if (response.tokenUsage() != null) usage = (usage == null ? 0 : usage) + response.tokenUsage();
        }
        return new AiEmbeddingBatchResponse(vectors, request.model(), usage);
    }
}
