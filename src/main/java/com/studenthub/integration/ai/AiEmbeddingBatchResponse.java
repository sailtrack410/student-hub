package com.studenthub.integration.ai;

import java.util.List;

/** Embeddings returned in the same order as the input list. */
public record AiEmbeddingBatchResponse(List<List<Double>> vectors, String model, Integer tokenUsage) {}
