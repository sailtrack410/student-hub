package com.studenthub.modules.aichat;

import com.studenthub.modules.aichat.intent.impl.AiAssistantDataServiceImpl;
import com.studenthub.modules.schedule.query.ScheduleQuery;
import com.studenthub.modules.servicecatalog.query.ServiceCatalogQuery;
import com.studenthub.modules.servicerequest.query.ServiceRequestQuery;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 增量架构规则：先锁住 AI 领域边界，其他既有模块可按领域逐步纳入。 */
class AiModuleBoundaryTest {
    @Test
    void aiBytecodeMustNotReferenceForeignPersistenceMappers() throws Exception {
        Pattern dependency = Pattern.compile("com/studenthub/modules/([a-zA-Z0-9_]+)/mapper/");
        List<String> violations = new ArrayList<>();
        Path root = Path.of("target/classes/com/studenthub/modules/aichat");
        assertTrue(Files.isDirectory(root));
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                var matcher = dependency.matcher(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1));
                while (matcher.find()) {
                    if (!matcher.group(1).equals("aichat")) {
                        violations.add(root.relativize(file) + " -> " + matcher.group(1));
                    }
                }
            }
        }
        assertEquals(List.of(), violations, "AI 跨领域数据访问应通过 owner query/application 接口");
    }

    @Test
    void assistantUsesQueryContractsAndImmutableProjections() {
        for (var field : AiAssistantDataServiceImpl.class.getDeclaredFields()) {
            String type = field.getType().getName();
            assertFalse(type.contains(".mapper.") || type.contains(".entity."), type);
        }
        for (Class<?> type : List.of(ScheduleQuery.Course.class, ScheduleQuery.Semester.class,
                ScheduleQuery.Snapshot.class, ServiceRequestQuery.Request.class, ServiceCatalogQuery.Item.class)) {
            assertTrue(type.isRecord(), type.getName());
            for (var field : type.getDeclaredFields()) {
                assertFalse(field.getType().getName().contains(".entity."), field.toString());
            }
        }
    }
}
