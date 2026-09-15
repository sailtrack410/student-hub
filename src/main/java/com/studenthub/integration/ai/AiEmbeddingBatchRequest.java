package com.studenthub.integration.ai;

import java.util.List;

/** OpenAI-compatible embeddings batch request. */
public record AiEmbeddingBatchRequest(String model, List<String> input) {}
