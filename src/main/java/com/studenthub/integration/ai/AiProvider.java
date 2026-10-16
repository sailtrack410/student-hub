package com.studenthub.integration.ai;

import java.util.ArrayList;
import java.util.List;

public interface AiProvider {

    AiChatResponse chat(AiChatRequest request);

    /**
     * 流式对话：增量文本通过 onDelta 回调推送，结束时调用 onDone(model, promptTokens, completionTokens)。
     * 供应商不支持流式时默认回退为阻塞调用一次性回调。
     *
     * @param onDone  结束回调，参数依次为模型名、prompt tokens（可空）、completion tokens（可空）
     */
    default void chatStream(AiChatRequest request, java.util.function.Consumer<String> onDelta,
                            TriConsumer<String, Integer, Integer> onDone) {
        AiChatResponse response = chat(request);
        onDelta.accept(response.content());
        onDone.accept(response.model(), response.promptTokens(), response.completionTokens());
    }

    /**
     * 流式对话（显式绑定）：绕过默认模型解析，直接按指定供应商绑定调用。
     * 默认实现回退为阻塞调用一次性回调。
     */
    default void chatStreamWithBinding(AiModelBinding binding, AiChatRequest request,
                                       java.util.function.Consumer<String> onDelta,
                                       TriConsumer<String, Integer, Integer> onDone) {
        chatStream(request, onDelta, onDone);
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
