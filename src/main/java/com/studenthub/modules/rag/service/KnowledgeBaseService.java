package com.studenthub.modules.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.ai.AiChatResponse;
import com.studenthub.integration.ai.AiMessage;
import com.studenthub.integration.ai.AiRuntimeConfig;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.rag.config.RagProperties;
import com.studenthub.modules.rag.controller.dto.RagTestResponse;
import com.studenthub.modules.rag.entity.*;
import com.studenthub.modules.rag.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.studenthub.common.util.Digests;
import com.studenthub.service.AiChatService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j @Service @RequiredArgsConstructor
public class KnowledgeBaseService {
    private final KnowledgeBaseMapper baseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final FileStorageService fileStorageService;
    private final KnowledgeDocumentParser parser;
    private final EmbeddingService embeddingService;
    private final RetrievalService retrievalService;
    private final AiChatService aiChatService;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;
    @Qualifier("ragJdbcTemplate") private final JdbcTemplate vectorJdbc;

    /** RAG 未启用时管理端/公开接口统一返回「知识库服务未启用」，避免误连未配置数据库。 */
    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库服务未启用");
        }
    }

    public List<KnowledgeBaseEntity> listBases() { requireEnabled(); return baseMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseEntity>().orderByDesc(KnowledgeBaseEntity::getUpdatedAt)); }
    public KnowledgeBaseEntity requireBase(Long id) { requireEnabled(); KnowledgeBaseEntity e=baseMapper.selectById(id); if(e==null) throw new BusinessException(ErrorCode.NOT_FOUND,"知识库不存在"); return e; }
    @Transactional public KnowledgeBaseEntity create(Long operator, com.studenthub.modules.rag.controller.dto.KnowledgeBaseRequest r) { requireEnabled();
        KnowledgeBaseEntity e=new KnowledgeBaseEntity(); e.setName(r.name().trim()); e.setDescription(r.description()); e.setVisibility(StringUtils.hasText(r.visibility())?r.visibility():"INTERNAL"); e.setEnabled(r.enabled()==null||r.enabled()); e.setChunkSize(r.chunkSize()==null?800:r.chunkSize()); e.setChunkOverlap(r.chunkOverlap()==null?120:r.chunkOverlap()); e.setTopK(r.topK()==null?6:r.topK()); e.setScoreThreshold(r.scoreThreshold()==null?new java.math.BigDecimal("0.30"):r.scoreThreshold()); e.setSystemPrompt(r.systemPrompt()); e.setAssistantEnabled(r.assistantEnabled()==null||r.assistantEnabled()); e.setRagTokenBudget(r.ragTokenBudget()==null?3000:r.ragTokenBudget()); e.setAssistantPriority(r.assistantPriority()==null?0:r.assistantPriority()); e.setDocumentCount(0); e.setChunkCount(0); baseMapper.insert(e); return e;
    }
    @Transactional public KnowledgeBaseEntity update(Long id, com.studenthub.modules.rag.controller.dto.KnowledgeBaseRequest r) { KnowledgeBaseEntity e=requireBase(id); e.setName(r.name().trim()); e.setDescription(r.description()); if(StringUtils.hasText(r.visibility()))e.setVisibility(r.visibility()); if(r.enabled()!=null)e.setEnabled(r.enabled()); if(r.chunkSize()!=null)e.setChunkSize(r.chunkSize()); if(r.chunkOverlap()!=null)e.setChunkOverlap(r.chunkOverlap()); if(r.topK()!=null)e.setTopK(r.topK()); if(r.scoreThreshold()!=null)e.setScoreThreshold(r.scoreThreshold()); if(r.assistantEnabled()!=null)e.setAssistantEnabled(r.assistantEnabled()); if(r.ragTokenBudget()!=null)e.setRagTokenBudget(r.ragTokenBudget()); if(r.assistantPriority()!=null)e.setAssistantPriority(r.assistantPriority()); e.setSystemPrompt(r.systemPrompt()); baseMapper.updateById(e); return e; }
    @Transactional public void delete(Long id) { requireBase(id); documentMapper.delete(new LambdaQueryWrapper<KnowledgeDocumentEntity>().eq(KnowledgeDocumentEntity::getKnowledgeBaseId,id)); vectorJdbc.update("DELETE FROM ai_knowledge_chunk WHERE knowledge_base_id=?",id); vectorJdbc.update("DELETE FROM ai_knowledge_chunk_stage WHERE knowledge_base_id=?",id); baseMapper.deleteById(id); }
    public List<KnowledgeDocumentEntity> documents(Long baseId) { requireBase(baseId); return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>().eq(KnowledgeDocumentEntity::getKnowledgeBaseId,baseId).orderByDesc(KnowledgeDocumentEntity::getCreatedAt)); }

    @Transactional
    public KnowledgeDocumentEntity upload(Long operator, Long baseId, byte[] bytes, String name, String type) {
        requireEnabled();
        KnowledgeBaseEntity base=requireBase(baseId);
        String contentHash = sha256(bytes);
        KnowledgeDocumentEntity duplicate = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, baseId)
                .eq(KnowledgeDocumentEntity::getContentHash, contentHash)
                .eq(KnowledgeDocumentEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (duplicate != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该文档已经存在于当前知识库");
        }
        FileStorageService.StoredFile file=fileStorageService.storeKnowledgeDocument(bytes,name,type,operator,properties.getMaxDocumentSizeMb());
        KnowledgeDocumentEntity doc=new KnowledgeDocumentEntity();
        doc.setKnowledgeBaseId(baseId); doc.setFileId(file.fileId()); doc.setFileName(file.fileName());
        doc.setContentType(type); doc.setFileSize((long)bytes.length); doc.setContentHash(contentHash);
        doc.setStatus("QUEUED"); doc.setChunkCount(0); doc.setIndexAttempts(0);
        doc.setProcessedChunks(0); doc.setTotalChunks(0); doc.setNextAttemptAt(LocalDateTime.now());
        documentMapper.insert(doc);
        refreshCounts(baseId);
        return doc;
    }
    @Transactional public void deleteDocument(Long baseId, Long docId) {
        requireBase(baseId); requireDocument(baseId,docId);
        // Main-row deletion serializes with the worker's final ownership lock.
        documentMapper.deleteById(docId);
        vectorJdbc.update("DELETE FROM ai_knowledge_chunk WHERE document_id=?",docId);
        vectorJdbc.update("DELETE FROM ai_knowledge_chunk_stage WHERE document_id=?",docId);
        refreshCounts(baseId);
    }
    @Transactional public KnowledgeDocumentEntity reindex(Long baseId, Long docId) {
        requireBase(baseId); requireDocument(baseId,docId);
        if (documentMapper.enqueue(docId) != 1) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID,"文档已在索引队列中");
        }
        return documentMapper.selectById(docId);
    }
    private KnowledgeDocumentEntity requireDocument(Long baseId,Long id){KnowledgeDocumentEntity d=documentMapper.selectById(id);if(d==null||!Objects.equals(d.getKnowledgeBaseId(),baseId))throw new BusinessException(ErrorCode.NOT_FOUND,"文档不存在");return d;}
    void refreshCounts(Long baseId) {
        List<KnowledgeDocumentEntity> documents = documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .select(KnowledgeDocumentEntity::getChunkCount).eq(KnowledgeDocumentEntity::getKnowledgeBaseId, baseId));
        int chunks = documents.stream().mapToInt(doc -> doc.getChunkCount() == null ? 0 : doc.getChunkCount()).sum();
        KnowledgeBaseEntity update = new KnowledgeBaseEntity();
        update.setId(baseId); update.setDocumentCount(documents.size()); update.setChunkCount(chunks);
        baseMapper.updateById(update);
    }
    public RagTestResponse test(Long baseId,String question,boolean generate){requireEnabled();long t=System.currentTimeMillis();KnowledgeBaseEntity b=requireBase(baseId); List<RagTestResponse.RagHit> hits=retrieve(List.of(baseId),question,b.getTopK(),b.getScoreThreshold()); long rt=System.currentTimeMillis()-t;String answer=null;long gt=0; if(generate&&!hits.isEmpty()){long s=System.currentTimeMillis();String context=hits.stream().map(h->"["+h.fileName()+" 第"+(h.page()==null?"-":h.page())+"页]\n"+h.content()).collect(Collectors.joining("\n\n"));String prompt="仅依据下列知识库资料回答问题；资料没有答案时明确说未找到，不要编造。回答末尾列出引用文件名和页码。\n\n资料：\n"+context+"\n\n问题："+question;try{AiChatResponse r=aiChatService.chat(List.of(new AiMessage("system",b.getSystemPrompt()==null?"你是校园知识库助手。":b.getSystemPrompt()),new AiMessage("user",prompt)));answer=r.content();}catch(Exception e){answer="知识库已召回相关资料，但回答模型暂不可用。";}gt=System.currentTimeMillis()-s;}var snapshot=AiRuntimeConfig.current();String model=snapshot!=null&&snapshot.embedding()!=null?snapshot.embedding().model():null;return new RagTestResponse(answer,hits,rt,gt,model);}

    /**
     * 混合检索：向量与词法各自独立召回后用 RRF 融合，检索逻辑见 {@link RetrievalService}。
     * 查询向量生成失败时降级为纯词法召回，不再让整次检索失败。
     */
    public List<RagTestResponse.RagHit> retrieve(List<Long> baseIds,String question,int topK,java.math.BigDecimal threshold){
        if (!properties.isEnabled()) return List.of();
        if (baseIds==null || baseIds.isEmpty() || question==null || question.isBlank()) return List.of();
        List<Double> queryVector=null;
        try { queryVector=embeddingService.embed(question).vector(); }
        catch(Exception e){ log.warn("知识库查询向量生成失败，本轮降级为纯词法召回: {}",e.getMessage()); }
        return retrievalService.retrieve(baseIds,question,queryVector,topK,threshold);
    }
    private String sha256(byte[] b){return Digests.sha256Hex(b);}
    private String trim(String s,int n){return s==null?"":s.length()>n?s.substring(0,n):s;}

    /**
     * AI 助手使用的知识库上下文（V54）：与 visibility 解耦，检索 enabled + assistantEnabled 的库；
     * 读每个库自己的 topK/scoreThreshold/ragTokenBudget，多库按 assistantPriority 降序合并去重，
     * 再按本次 token 预算装包。公开接口仍只允许 visibility=PUBLIC（见 PublicKnowledgeController）。
     */
    public RagContext assistantContext(String question, int tokenBudget) {
        if (!properties.isEnabled()) return RagContext.empty();
        List<KnowledgeBaseEntity> bases = baseMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getEnabled, true)
                .eq(KnowledgeBaseEntity::getAssistantEnabled, true)
                .orderByDesc(KnowledgeBaseEntity::getAssistantPriority)
                .orderByDesc(KnowledgeBaseEntity::getUpdatedAt));
        if (bases.isEmpty()) return RagContext.empty();
        List<RagTestResponse.RagHit> merged = new ArrayList<>();
        java.util.Set<Long> seenChunks = new java.util.HashSet<>();
        for (KnowledgeBaseEntity base : bases) {
            int kbTopK = base.getTopK() == null ? 5 : base.getTopK();
            int kbBudget = base.getRagTokenBudget() == null ? 3000 : base.getRagTokenBudget();
            java.math.BigDecimal threshold = base.getScoreThreshold() == null
                    ? new java.math.BigDecimal("0.30") : base.getScoreThreshold();
            List<RagTestResponse.RagHit> hits = retrieve(List.of(base.getId()), question, kbTopK, threshold);
            for (RagTestResponse.RagHit hit : hits) {
                if (seenChunks.add(hit.chunkId())) {
                    merged.add(hit);
                }
            }
            // 每库最多贡献 kbBudget 内的候选，避免单库挤占全部预算；最终仍按总预算再裁
            if (merged.size() > 200) break;
        }
        if (merged.isEmpty()) return RagContext.empty();
        merged.sort(java.util.Comparator.comparingDouble(RagTestResponse.RagHit::score).reversed());
        List<RagTestResponse.RagHit> packed = new ArrayList<>();
        int used = 0;
        for (RagTestResponse.RagHit hit : merged) {
            String header = "[来源：" + hit.fileName()
                    + (hit.page() == null ? "" : "，第" + hit.page() + "页")
                    + (hit.titlePath() == null || hit.titlePath().isBlank() ? "" : "，" + hit.titlePath()) + "]";
            int cost = com.studenthub.common.util.TokenEstimator.estimate(header + hit.content(), 1.5) + 8;
            if (used + cost > tokenBudget && !packed.isEmpty()) {
                break;
            }
            packed.add(hit);
            used += cost;
            if (used >= tokenBudget) break;
        }
        if (packed.isEmpty()) return RagContext.empty();
        String text = packed.stream().map(h -> "[来源：" + h.fileName()
                        + (h.page() == null ? "" : "，第" + h.page() + "页")
                        + (h.titlePath() == null || h.titlePath().isBlank() ? "" : "，" + h.titlePath()) + "]\n"
                        + h.content())
                .collect(Collectors.joining("\n\n"));
        return new RagContext(text, packed);
    }

    /** 知识库检索结果：拼好的上下文 + 结构化来源（供 App 展示引用）。 */
    public record RagContext(String text, List<RagTestResponse.RagHit> hits) {
        public static RagContext empty() { return new RagContext(null, List.of()); }
    }
}
