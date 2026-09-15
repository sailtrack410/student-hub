package com.studenthub.integration.ai;

import java.util.ArrayList;
import java.util.List;

public interface AiProvider {

    AiChatResponse chat(AiChatRequest request);

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
