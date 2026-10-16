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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
@ConditionalOnBean(name = "ragJdbcTemplate")
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

    public List<KnowledgeBaseEntity> listBases() { return baseMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseEntity>().orderByDesc(KnowledgeBaseEntity::getUpdatedAt)); }
    public KnowledgeBaseEntity requireBase(Long id) { KnowledgeBaseEntity e=baseMapper.selectById(id); if(e==null) throw new BusinessException(ErrorCode.NOT_FOUND,"知识库不存在"); return e; }
    @Transactional public KnowledgeBaseEntity create(Long operator, com.studenthub.modules.rag.controller.dto.KnowledgeBaseRequest r) {
        KnowledgeBaseEntity e=new KnowledgeBaseEntity(); e.setName(r.name().trim()); e.setDescription(r.description()); e.setVisibility(StringUtils.hasText(r.visibility())?r.visibility():"INTERNAL"); e.setEnabled(r.enabled()==null||r.enabled()); e.setChunkSize(r.chunkSize()==null?800:r.chunkSize()); e.setChunkOverlap(r.chunkOverlap()==null?120:r.chunkOverlap()); e.setTopK(r.topK()==null?6:r.topK()); e.setScoreThreshold(r.scoreThreshold()==null?new java.math.BigDecimal("0.30"):r.scoreThreshold()); e.setSystemPrompt(r.systemPrompt()); e.setDocumentCount(0); e.setChunkCount(0); baseMapper.insert(e); return e;
    }
    @Transactional public KnowledgeBaseEntity update(Long id, com.studenthub.modules.rag.controller.dto.KnowledgeBaseRequest r) { KnowledgeBaseEntity e=requireBase(id); e.setName(r.name().trim()); e.setDescription(r.description()); if(StringUtils.hasText(r.visibility()))e.setVisibility(r.visibility()); if(r.enabled()!=null)e.setEnabled(r.enabled()); if(r.chunkSize()!=null)e.setChunkSize(r.chunkSize()); if(r.chunkOverlap()!=null)e.setChunkOverlap(r.chunkOverlap()); if(r.topK()!=null)e.setTopK(r.topK()); if(r.scoreThreshold()!=null)e.setScoreThreshold(r.scoreThreshold()); e.setSystemPrompt(r.systemPrompt()); baseMapper.updateById(e); return e; }
    @Transactional public void delete(Long id) { requireBase(id); documentMapper.delete(new LambdaQueryWrapper<KnowledgeDocumentEntity>().eq(KnowledgeDocumentEntity::getKnowledgeBaseId,id)); vectorJdbc.update("DELETE FROM ai_knowledge_chunk WHERE knowledge_base_id=?",id); baseMapper.deleteById(id); }
    public List<KnowledgeDocumentEntity> documents(Long baseId) { requireBase(baseId); return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>().eq(KnowledgeDocumentEntity::getKnowledgeBaseId,baseId).orderByDesc(KnowledgeDocumentEntity::getCreatedAt)); }

    @Transactional
    public KnowledgeDocumentEntity upload(Long operator, Long baseId, byte[] bytes, String name, String type) {
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
        KnowledgeDocumentEntity doc=new KnowledgeDocumentEntity(); doc.setKnowledgeBaseId(baseId); doc.setFileId(file.fileId()); doc.setFileName(file.fileName()); doc.setContentType(type); doc.setFileSize((long)bytes.length); doc.setContentHash(contentHash); doc.setStatus("PROCESSING"); doc.setChunkCount(0); documentMapper.insert(doc);
        try { index(base,doc,bytes,name); doc.setStatus("SUCCESS"); doc.setIndexedAt(LocalDateTime.now()); documentMapper.updateById(doc); refreshCounts(baseId); }
        catch(Exception ex) { log.error("知识库文档索引失败, docId={}",doc.getId(),ex); vectorJdbc.update("DELETE FROM ai_knowledge_chunk WHERE document_id=?", doc.getId()); doc.setChunkCount(0); doc.setStatus("FAILED"); doc.setErrorMessage(trim(ex.getMessage(),1000)); documentMapper.updateById(doc); refreshCounts(baseId); }
        return doc;
    }
    @Transactional public void deleteDocument(Long baseId, Long docId) { requireBase(baseId); KnowledgeDocumentEntity d=requireDocument(baseId,docId); vectorJdbc.update("DELETE FROM ai_knowledge_chunk WHERE document_id=?",docId); documentMapper.deleteById(docId); refreshCounts(baseId); }
    @Transactional public KnowledgeDocumentEntity reindex(Long baseId, Long docId) { KnowledgeBaseEntity b=requireBase(baseId); KnowledgeDocumentEntity d=requireDocument(baseId,docId); byte[] bytes=fileStorageService.readContent(d.getFileId()).bytes(); vectorJdbc.update("DELETE FROM ai_knowledge_chunk WHERE document_id=?",docId); d.setStatus("PROCESSING"); d.setErrorMessage(null); d.setIndexedAt(null); documentMapper.updateById(d); try {index(b,d,bytes,d.getFileName()); d.setStatus("SUCCESS"); d.setIndexedAt(LocalDateTime.now()); documentMapper.updateById(d); refreshCounts(baseId); } catch(Exception e){vectorJdbc.update("DELETE FROM ai_knowledge_chunk WHERE document_id=?", docId);d.setChunkCount(0);d.setStatus("FAILED");d.setErrorMessage(trim(e.getMessage(),1000));documentMapper.updateById(d);refreshCounts(baseId);} return d; }
    private KnowledgeDocumentEntity requireDocument(Long baseId,Long id){KnowledgeDocumentEntity d=documentMapper.selectById(id);if(d==null||!Objects.equals(d.getKnowledgeBaseId(),baseId))throw new BusinessException(ErrorCode.NOT_FOUND,"文档不存在");return d;}
    private void refreshCounts(Long baseId){int docs=documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>().eq(KnowledgeDocumentEntity::getKnowledgeBaseId,baseId)).intValue();Integer chunks=vectorJdbc.queryForObject("SELECT count(*) FROM ai_knowledge_chunk WHERE knowledge_base_id=?",Integer.class,baseId); KnowledgeBaseEntity u=new KnowledgeBaseEntity();u.setId(baseId);u.setDocumentCount(docs);u.setChunkCount(chunks==null?0:chunks);baseMapper.updateById(u);}
    private void index(KnowledgeBaseEntity base, KnowledgeDocumentEntity doc, byte[] bytes, String name) {
        List<KnowledgeDocumentParser.Section> sections=parser.parse(bytes,name); List<Chunk> chunks=chunk(sections,base.getChunkSize(),base.getChunkOverlap()); int ordinal=0;
        int batchSize=Math.max(1, properties.getEmbeddingBatchSize());
        for (int from=0; from<chunks.size(); from+=batchSize) {
            int to=Math.min(chunks.size(), from+batchSize);
            List<Chunk> batch=chunks.subList(from,to);
            List<EmbeddingService.EmbeddingResult> embeddings=embeddingService.embedBatch(batch.stream().map(Chunk::content).toList());
            for (int i=0;i<batch.size();i++) {
                Chunk c=batch.get(i); EmbeddingService.EmbeddingResult emb=embeddings.get(i); String vector=arrayLiteral(emb.vector()); String metadata; try{metadata=objectMapper.writeValueAsString(Map.of("page",c.page()==null?0:c.page(),"titlePath",c.titlePath()==null?"":c.titlePath(),"ordinal",ordinal));}catch(Exception e){metadata="{}";}
                // 记录向量模型与维度：换模型后可据此识别历史分块并提示重建，避免余弦静默算错
                vectorJdbc.update("INSERT INTO ai_knowledge_chunk(knowledge_base_id,document_id,ordinal,title_path,content,content_hash,metadata,embedding,embedding_model,embedding_dim) VALUES(?,?,?,?,?,?,?::jsonb,?::double precision[],?,?) ON CONFLICT (knowledge_base_id, content_hash) DO NOTHING",base.getId(),doc.getId(),ordinal,c.titlePath(),c.content(),sha256(c.content().getBytes(StandardCharsets.UTF_8)),metadata,vector,emb.model(),emb.vector().size()); ordinal++;
            }
        }
        doc.setChunkCount(ordinal);
    }
    private List<Chunk> chunk(List<KnowledgeDocumentParser.Section> sections,int size,int overlap){List<Chunk> out=new ArrayList<>();for(var s:sections){String text=s.text();int start=0;while(start<text.length()){int end=Math.min(text.length(),start+size);if(end<text.length()){int cut=Math.max(text.lastIndexOf('\n',end),text.lastIndexOf('。',end));if(cut>start+size/2)end=cut+1;}String c=text.substring(start,end).trim();if(!c.isBlank())out.add(new Chunk(s.titlePath(),s.page(),c));if(end>=text.length())break;start=Math.max(start+1,end-overlap);}}return out;}
    private String arrayLiteral(List<Double> v){return "{"+v.stream().map(x->Double.toString(x==null?0:x)).collect(Collectors.joining(","))+"}";}
    public RagTestResponse test(Long baseId,String question,boolean generate){long t=System.currentTimeMillis();KnowledgeBaseEntity b=requireBase(baseId); List<RagTestResponse.RagHit> hits=retrieve(List.of(baseId),question,b.getTopK(),b.getScoreThreshold()); long rt=System.currentTimeMillis()-t;String answer=null;long gt=0; if(generate&&!hits.isEmpty()){long s=System.currentTimeMillis();String context=hits.stream().map(h->"["+h.fileName()+" 第"+(h.page()==null?"-":h.page())+"页]\n"+h.content()).collect(Collectors.joining("\n\n"));String prompt="仅依据下列知识库资料回答问题；资料没有答案时明确说未找到，不要编造。回答末尾列出引用文件名和页码。\n\n资料：\n"+context+"\n\n问题："+question;try{AiChatResponse r=aiChatService.chat(List.of(new AiMessage("system",b.getSystemPrompt()==null?"你是校园知识库助手。":b.getSystemPrompt()),new AiMessage("user",prompt)));answer=r.content();}catch(Exception e){answer="知识库已召回相关资料，但回答模型暂不可用。";}gt=System.currentTimeMillis()-s;}var snapshot=AiRuntimeConfig.current();String model=snapshot!=null&&snapshot.embedding()!=null?snapshot.embedding().model():null;return new RagTestResponse(answer,hits,rt,gt,model);}

    /**
     * 混合检索：向量与词法各自独立召回后用 RRF 融合，检索逻辑见 {@link RetrievalService}。
     * 查询向量生成失败时降级为纯词法召回，不再让整次检索失败。
     */
    public List<RagTestResponse.RagHit> retrieve(List<Long> baseIds,String question,int topK,java.math.BigDecimal threshold){
        if (baseIds==null || baseIds.isEmpty() || question==null || question.isBlank()) return List.of();
        List<Double> queryVector=null;
        try { queryVector=embeddingService.embed(question).vector(); }
        catch(Exception e){ log.warn("知识库查询向量生成失败，本轮降级为纯词法召回: {}",e.getMessage()); }
        return retrievalService.retrieve(baseIds,question,queryVector,topK,threshold);
    }
    private String sha256(byte[] b){return Digests.sha256Hex(b);}
    private String trim(String s,int n){return s==null?"":s.length()>n?s.substring(0,n):s;}
    private record Chunk(String titlePath,Integer page,String content){}

    /** AI 助手使用的公共校园知识上下文；个人/工单数据仍由原有意图链路处理。 */
    public String assistantContext(String question) {
        List<Long> ids = baseMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getEnabled, true)
                .eq(KnowledgeBaseEntity::getVisibility, "PUBLIC"))
                .stream().map(KnowledgeBaseEntity::getId).toList();
        if (ids.isEmpty()) return null;
        List<RagTestResponse.RagHit> hits = retrieve(ids, question, 6, new java.math.BigDecimal("0.30"));
        if (hits.isEmpty()) return null;
        return hits.stream().map(h -> "[来源：" + h.fileName() + (h.page() == null ? "" : "，第" + h.page() + "页") + "]\n" + h.content()).collect(Collectors.joining("\n\n"));
    }
}
