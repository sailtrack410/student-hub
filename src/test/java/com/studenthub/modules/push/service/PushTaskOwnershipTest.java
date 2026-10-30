package com.studenthub.modules.push.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.studenthub.modules.push.entity.PushTaskEntity;
import com.studenthub.modules.push.mapper.PushTaskMapper;
import com.studenthub.modules.rag.mapper.KnowledgeDocumentMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PushTaskOwnershipTest {
    private SqlSessionFactory factory(JdbcDataSource ds, Class<?> mapper) {
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        config.addMapper(mapper);
        return new MybatisSqlSessionFactoryBuilder().build(config);
    }
    private JdbcDataSource source() {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:claims-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1"); return ds;
    }
    private PushTaskEntity snapshot() {
        var task = new PushTaskEntity(); task.setId(1L); task.setCampusId(2L); task.setStatus("SCHEDULED"); task.setVersion(0); return task;
    }

    @Test void concurrentDatabaseClaimsHaveExactlyOneWinnerAndFenceCompletion() throws Exception {
        var ds = source(); var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE push_task(id BIGINT PRIMARY KEY,campus_id BIGINT,status VARCHAR,execution_token VARCHAR,started_at TIMESTAMP,finished_at TIMESTAMP,last_error VARCHAR,updated_at TIMESTAMP,version INT,deleted INT,total_count INT,success_count INT,failed_count INT,delivered_count INT,clicked_count INT,skipped_count INT,not_configured_count INT,retry_count INT)");
        jdbc.update("INSERT INTO push_task(id,campus_id,status,version,deleted) VALUES(1,2,'SCHEDULED',0,0)");
        var sessions = factory(ds, PushTaskMapper.class);
        var executor = Executors.newFixedThreadPool(8); var gate = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Integer>>();
            for (int i=0; i<8; i++) { final String token="worker-"+i; futures.add(executor.submit(() -> { gate.await(); try(var session=sessions.openSession(true)) { return session.getMapper(PushTaskMapper.class).claim(snapshot(),token); } })); }
            gate.countDown(); int won=0; for(var future:futures) won+=future.get(10,TimeUnit.SECONDS);
            assertEquals(1,won);
            String owner=jdbc.queryForObject("SELECT execution_token FROM push_task WHERE id=1",String.class);
            try(var session=sessions.openSession(true)) {
                var mapper=session.getMapper(PushTaskMapper.class);
                var wrong=snapshot(); wrong.setExecutionToken("stale"); wrong.setStatus("FAILED");
                assertEquals(0,mapper.finishOwned(wrong));
                assertEquals(0,mapper.touchOwned(wrong));
                wrong.setExecutionToken(owner);
                assertEquals(1,mapper.touchOwned(wrong));
                assertEquals(0,mapper.recoverInterrupted(LocalDateTime.now().minusMinutes(30)));
                assertEquals(0,mapper.deleteIdle(snapshot()));
                wrong.setExecutionToken(owner); wrong.setStatus("SENT");
                assertEquals(1,mapper.finishOwned(wrong));
                assertEquals(0,mapper.finishOwned(wrong));
            }
            assertEquals("SENT",jdbc.queryForObject("SELECT status FROM push_task WHERE id=1",String.class));
            assertNull(jdbc.queryForObject("SELECT execution_token FROM push_task WHERE id=1",String.class));
        } finally { executor.shutdownNow(); }
    }

    @Test void ragLeaseRecoveryRejectsOldWorkerProgressAndCompletion() {
        var ds=source(); var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE ai_knowledge_document(id BIGINT PRIMARY KEY,status VARCHAR,index_token VARCHAR,index_lease_until TIMESTAMP,index_attempts INT,processed_chunks INT,total_chunks INT,next_attempt_at TIMESTAMP,version INT,deleted INT,chunk_count INT,indexed_at TIMESTAMP,error_message VARCHAR,created_at TIMESTAMP)");
        jdbc.update("INSERT INTO ai_knowledge_document(id,status,index_attempts,version,deleted,created_at) VALUES(1,'QUEUED',0,0,0,CURRENT_TIMESTAMP)");
        var sessions=factory(ds,KnowledgeDocumentMapper.class); var now=LocalDateTime.now();
        try(var session=sessions.openSession(true)) {
            var mapper=session.getMapper(KnowledgeDocumentMapper.class);
            assertEquals(1,mapper.claim(1L,"first",now,now.plusMinutes(1),3));
            assertEquals(0,mapper.claim(1L,"second",now,now.plusMinutes(1),3));
            assertEquals(1,mapper.claim(1L,"second",now.plusMinutes(2),now.plusMinutes(3),3));
            assertEquals(0,mapper.progress(1L,"first",99,99,now.plusMinutes(5)));
            assertEquals(0,mapper.finish(1L,"first",99,now));
            assertEquals(0,mapper.fail(1L,"first","FAILED","old worker",null));
            assertEquals(1,mapper.progress(1L,"second",1,2,now.plusMinutes(4)));
            assertEquals(1,mapper.finish(1L,"second",2,now));
            assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM ai_knowledge_document WHERE id=1",String.class));
            assertEquals(2,jdbc.queryForObject("SELECT index_attempts FROM ai_knowledge_document WHERE id=1",Integer.class));
        }
    }

    @Test void abandonedPushExecutionIsFailedWithoutBeingRescheduled() {
        var ds = source(); var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE push_task(id BIGINT PRIMARY KEY,status VARCHAR,execution_token VARCHAR,updated_at TIMESTAMP,finished_at TIMESTAMP,last_error VARCHAR,version INT,deleted INT)");
        jdbc.update("INSERT INTO push_task(id,status,execution_token,updated_at,version,deleted) VALUES(1,'SENDING','lost-worker',?,1,0)", LocalDateTime.now().minusHours(1));
        var sessions = factory(ds, PushTaskMapper.class);
        try (var session = sessions.openSession(true)) {
            assertEquals(1, session.getMapper(PushTaskMapper.class).recoverInterrupted(LocalDateTime.now().minusMinutes(30)));
        }
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM push_task WHERE id=1", String.class));
        assertNull(jdbc.queryForObject("SELECT execution_token FROM push_task WHERE id=1", String.class));
        assertTrue(jdbc.queryForObject("SELECT last_error FROM push_task WHERE id=1", String.class).contains("PENDING"));
    }
}
