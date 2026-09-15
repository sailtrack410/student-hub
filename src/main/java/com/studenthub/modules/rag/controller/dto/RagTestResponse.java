package com.studenthub.modules.rag.controller.dto;
import java.util.List;
public record RagTestResponse(String answer, List<RagHit> hits, long retrievalMs, long generationMs, String embeddingModel) {
    /**
     * 一条召回结果。
     *
     * @param score        归一化后的融合分（0~1，可跨查询比较，用于阈值闸门）
     * @param vectorScore  向量通道余弦（未参与该通道时为 0）
     * @param keywordScore 词法通道三元组相似度（未参与该通道时为 0）
     * @param vectorRank   在向量通道内的排名，null 表示未被该通道召回
     * @param lexicalRank  在词法通道内的排名，null 表示未被该通道召回
     */
    public record RagHit(Long chunkId, Long documentId, String fileName, Integer ordinal, Integer page,
                         String titlePath, String content, double score, double vectorScore, double keywordScore,
                         Integer vectorRank, Integer lexicalRank) {}
}