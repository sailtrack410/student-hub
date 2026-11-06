package com.studenthub.modules.servicerequest;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real migrations on a disposable database; no application server or shared data source. */
class ServiceFlowMigrationIntegrationTest {

    @Test
    void migrationPreservesHistoricalDataAndCompensationIsIdempotent() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:service-flow-migration-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        source.setUser("sa");
        source.setPassword("");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        try {
            String baselineVersion = java.util.Arrays.stream(migration(source, "latest").info().all())
                    .map(MigrationInfo::getVersion).filter(java.util.Objects::nonNull)
                    .filter(version -> version.compareTo(MigrationVersion.fromVersion("109")) < 0)
                    .max(MigrationVersion::compareTo).orElseThrow().toString();
            migration(source, baselineVersion).migrate();
            assertEquals(baselineVersion, migration(source, baselineVersion).info().current().getVersion().toString());

            insertRequest(jdbc, 91001, 2001, "请假申请", 200, "COMPLETED");
            insertRequest(jdbc, 91002, 2001, "请假申请", 200, "PROCESSING");
            insertRequest(jdbc, 91003, 3001, "在读证明申请", 300, "SUBMITTED");
            insertRequest(jdbc, 91004, 1001, "宿舍报修", 100, "PROCESSING");
            insertOrder(jdbc, 92001, 91001, 2001, "COMPLETED");
            insertOrder(jdbc, 92002, 91002, 2001, "ASSIGNED");
            insertOrder(jdbc, 92003, 91003, 3001, "PENDING_DISPATCH");
            insertOrder(jdbc, 92004, 91004, 1001, "PROCESSING");

            Map<Long, Map<String, Object>> beforeRequests = snapshot(jdbc, "service_request", 91001, 91002, 91003, 91004);
            Map<Long, Map<String, Object>> beforeOrders = snapshot(jdbc, "work_order", 92001, 92002, 92003, 92004);
            jdbc.update("""
                    INSERT INTO service_request_log
                        (id, request_id, work_order_id, action, from_status, to_status, operator_id,
                         operator_name, comment, created_at, updated_at, created_by, updated_by, deleted, version)
                    VALUES (93001, 91001, 92001, 'COMPLETE', 'PROCESSING', 'COMPLETED', 1003,
                            '原处理人', '历史处理记录', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1003, 1003, 0, 0)
                    """);
            Map<String, Object> beforeLog = jdbc.queryForMap("SELECT * FROM service_request_log WHERE id=93001");

            var migrated = migration(source, "109").migrate();
            assertEquals(1, migrated.migrationsExecuted);
            assertEquals("109", migration(source, "109").info().current().getVersion().toString());
            assertOriginalColumns(jdbc, "service_request", beforeRequests, Set.of());
            assertOriginalColumns(jdbc, "work_order", beforeOrders, Set.of());
            assertEquals(beforeLog, jdbc.queryForMap("SELECT * FROM service_request_log WHERE id=93001"));

            assertEquals("LEAVE_APPROVAL", jdbc.queryForObject("SELECT flow_type FROM service_request WHERE id=91001", String.class));
            assertEquals("AFFAIR-LEAVE", jdbc.queryForObject("SELECT item_code FROM service_request WHERE id=91001", String.class));
            assertEquals("MANUAL", jdbc.queryForObject("SELECT flow_type FROM service_request WHERE id=91003", String.class));
            assertEquals("REPAIR", jdbc.queryForObject("SELECT flow_type FROM service_request WHERE id=91004", String.class));
            for (long orderId : List.of(92001L, 92002L, 92003L)) {
                assertTrue(jdbc.queryForObject("SELECT flow_archived FROM work_order WHERE id=?", Boolean.class, orderId));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_flow_repair_audit WHERE work_order_id=?", Integer.class, orderId));
                assertEquals("FLOW_DETACH", jdbc.queryForObject("SELECT action FROM service_request_log WHERE id=?", String.class, -orderId));
                assertEquals(jdbc.queryForObject("SELECT original_request_status FROM service_flow_repair_audit WHERE work_order_id=?", String.class, orderId),
                        jdbc.queryForObject("SELECT from_status FROM service_request_log WHERE id=?", String.class, -orderId));
                assertEquals(jdbc.queryForObject("SELECT from_status FROM service_request_log WHERE id=?", String.class, -orderId),
                        jdbc.queryForObject("SELECT to_status FROM service_request_log WHERE id=?", String.class, -orderId));
            }
            assertFalse(jdbc.queryForObject("SELECT flow_archived FROM work_order WHERE id=92004", Boolean.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_flow_repair_audit WHERE work_order_id=92004", Integer.class));

            // A late legacy writer creates another nonrepair order after the migration.
            insertRequest(jdbc, 91005, 3001, "在读证明申请", 300, "PROCESSING");
            jdbc.update("UPDATE service_request SET flow_type='MANUAL', item_code='CERT-ENROLL' WHERE id=91005");
            insertOrder(jdbc, 92005, 91005, 3001, "ASSIGNED");
            Map<Long, Map<String, Object>> lateRequest = snapshot(jdbc, "service_request", 91005);
            Map<Long, Map<String, Object>> lateOrder = snapshot(jdbc, "work_order", 92005);
            reconcile(source);
            assertTrue(jdbc.queryForObject("SELECT flow_archived FROM work_order WHERE id=92005", Boolean.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_flow_repair_audit WHERE work_order_id=92005", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_request_log WHERE id=-92005 AND action='FLOW_DETACH'", Integer.class));
            assertOriginalColumns(jdbc, "service_request", lateRequest, Set.of());
            assertOriginalColumns(jdbc, "work_order", lateOrder, Set.of("FLOW_ARCHIVED"));
            List<Map<String, Object>> auditAfterFirstRun = jdbc.queryForList("SELECT * FROM service_flow_repair_audit ORDER BY work_order_id");
            List<Map<String, Object>> logsAfterFirstRun = jdbc.queryForList("SELECT * FROM service_request_log ORDER BY id");
            List<Map<String, Object>> ordersAfterFirstRun = jdbc.queryForList("SELECT * FROM work_order ORDER BY id");

            reconcile(source);
            assertEquals(auditAfterFirstRun, jdbc.queryForList("SELECT * FROM service_flow_repair_audit ORDER BY work_order_id"));
            assertEquals(logsAfterFirstRun, jdbc.queryForList("SELECT * FROM service_request_log ORDER BY id"));
            assertEquals(ordersAfterFirstRun, jdbc.queryForList("SELECT * FROM work_order ORDER BY id"));
            assertOriginalColumns(jdbc, "service_request", beforeRequests, Set.of());
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }

    private Flyway migration(JdbcDataSource source, String target) {
        return Flyway.configure().dataSource(source)
                .locations("classpath:db/migration", "classpath:db/devdata")
                .target(target).load();
    }

    private void reconcile(JdbcDataSource source) throws Exception {
        Path script = Path.of("tools", "service-flow", "reconcile_nonrepair.sql").toAbsolutePath();
        assertTrue(script.toFile().isFile(), "Missing real compensation SQL: " + script);
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(script));
            connection.commit();
        }
    }

    private void insertRequest(JdbcTemplate jdbc, long id, long itemId, String itemName, long categoryId, String status) {
        jdbc.update("""
                INSERT INTO service_request
                    (id, request_no, campus_id, item_id, item_name, category_id, requester_id, title, form_data,
                     status, handler_organization_id, submitted_at, completed_at, created_at, updated_at,
                     created_by, updated_by, deleted, version)
                VALUES (?, ?, 1, ?, ?, ?, 1002, '历史业务申请', '{"reason":"历史申请数据"}', ?, 12,
                        TIMESTAMP '2026-11-01 09:00:00', TIMESTAMP '2026-11-02 12:00:00',
                        TIMESTAMP '2026-11-01 09:00:00', TIMESTAMP '2026-11-02 12:00:00', 1002, 1003, 0, 7)
                """, id, "SR-MIGRATION-" + id, itemId, itemName, categoryId, status);
    }

    private void insertOrder(JdbcTemplate jdbc, long id, long requestId, long itemId, String status) {
        jdbc.update("""
                INSERT INTO work_order
                    (id, order_no, request_id, campus_id, item_id, title, assigned_organization_id, assignee_id,
                     assignee_name, status, priority, complete_remark, assigned_at, started_at, completed_at,
                     arrival_photo_file_id, completion_photo_file_id, completion_photo_file_ids,
                     created_at, updated_at, created_by, updated_by, deleted, version)
                VALUES (?, ?, ?, 1, ?, '历史业务工单', 12, 1003, '原处理人', ?, 'HIGH', '保留原处理说明',
                        TIMESTAMP '2026-11-01 10:00:00', TIMESTAMP '2026-11-01 11:00:00', TIMESTAMP '2026-11-02 12:00:00',
                        711, 713, '[713,714]', TIMESTAMP '2026-11-01 09:00:00', TIMESTAMP '2026-11-02 12:00:00',
                        1004, 1003, 0, 9)
                """, id, "WO-MIGRATION-" + id, requestId, itemId, status);
    }

    private Map<Long, Map<String, Object>> snapshot(JdbcTemplate jdbc, String table, long... ids) {
        Map<Long, Map<String, Object>> records = new LinkedHashMap<>();
        for (long id : ids) records.put(id, jdbc.queryForMap("SELECT * FROM " + table + " WHERE id=?", id));
        return records;
    }

    private void assertOriginalColumns(JdbcTemplate jdbc, String table,
                                       Map<Long, Map<String, Object>> before, Set<String> excludedColumns) {
        before.forEach((id, columns) -> {
            Map<String, Object> after = jdbc.queryForMap("SELECT * FROM " + table + " WHERE id=?", id);
            columns.forEach((column, value) -> {
                if (!excludedColumns.contains(column.toUpperCase(java.util.Locale.ROOT))) {
                    assertEquals(value, after.get(column), table + "." + column + " for " + id);
                }
            });
        });
    }
}
