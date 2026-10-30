package com.studenthub.modules.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.rag.entity.KnowledgeDocumentEntity;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity> {
    @Select("SELECT * FROM ai_knowledge_document WHERE deleted=0 AND index_attempts<#{maxAttempts} "
            + "AND ((status='QUEUED' AND (next_attempt_at IS NULL OR next_attempt_at<=#{now})) "
            + "OR (status='PROCESSING' AND (index_lease_until IS NULL OR index_lease_until<#{now}))) "
            + "ORDER BY created_at LIMIT 5")
    List<KnowledgeDocumentEntity> due(@Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE ai_knowledge_document SET status='PROCESSING', index_token=#{token}, index_lease_until=#{lease}, "
            + "index_attempts=index_attempts+1, processed_chunks=0, total_chunks=0, version=version+1 "
            + "WHERE id=#{id} AND deleted=0 AND index_attempts<#{maxAttempts} "
            + "AND ((status='QUEUED' AND (next_attempt_at IS NULL OR next_attempt_at<=#{now})) "
            + "OR (status='PROCESSING' AND (index_lease_until IS NULL OR index_lease_until<#{now})))")
    int claim(@Param("id") Long id, @Param("token") String token, @Param("now") LocalDateTime now,
              @Param("lease") LocalDateTime lease, @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE ai_knowledge_document SET processed_chunks=#{processed}, total_chunks=#{total}, index_lease_until=#{lease} "
            + "WHERE id=#{id} AND deleted=0 AND status='PROCESSING' AND index_token=#{token}")
    int progress(@Param("id") Long id, @Param("token") String token, @Param("processed") int processed,
                 @Param("total") int total, @Param("lease") LocalDateTime lease);

    @Select("SELECT * FROM ai_knowledge_document WHERE id=#{id} AND deleted=0 AND status='PROCESSING' AND index_token=#{token} FOR UPDATE")
    KnowledgeDocumentEntity lockOwned(@Param("id") Long id, @Param("token") String token);

    @Update("UPDATE ai_knowledge_document SET status='SUCCESS', chunk_count=#{chunks}, processed_chunks=#{chunks}, "
            + "total_chunks=#{chunks}, indexed_at=#{now}, error_message=NULL, index_token=NULL, index_lease_until=NULL, "
            + "next_attempt_at=NULL, version=version+1 WHERE id=#{id} AND deleted=0 AND status='PROCESSING' AND index_token=#{token}")
    int finish(@Param("id") Long id, @Param("token") String token, @Param("chunks") int chunks, @Param("now") LocalDateTime now);

    @Update("UPDATE ai_knowledge_document SET status=#{status}, error_message=#{error}, next_attempt_at=#{next}, "
            + "index_token=NULL, index_lease_until=NULL, version=version+1 WHERE id=#{id} AND deleted=0 "
            + "AND status='PROCESSING' AND index_token=#{token}")
    int fail(@Param("id") Long id, @Param("token") String token, @Param("status") String status,
             @Param("error") String error, @Param("next") LocalDateTime next);

    @Update("UPDATE ai_knowledge_document SET status='QUEUED', index_attempts=0, processed_chunks=0, total_chunks=0, "
            + "index_token=NULL, index_lease_until=NULL, next_attempt_at=CURRENT_TIMESTAMP, error_message=NULL, version=version+1 "
            + "WHERE id=#{id} AND deleted=0 AND status NOT IN ('QUEUED','PROCESSING')")
    int enqueue(@Param("id") Long id);

    @Update("UPDATE ai_knowledge_document SET status='FAILED', index_token=NULL, index_lease_until=NULL, "
            + "error_message='索引执行中断且已达到重试上限；现有索引保留', version=version+1 "
            + "WHERE deleted=0 AND status='PROCESSING' AND index_attempts>=#{maxAttempts} "
            + "AND (index_lease_until IS NULL OR index_lease_until<#{now})")
    int expireExhausted(@Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);
}
