package com.studenthub.integration.ai;
import java.util.List;
public record AiEmbeddingResponse(List<Double> vector, String model, Integer tokenUsage) {}
