package com.studenthub.modules.systemconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.modules.community.AdminApiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** SYS06 定时任务启停：真实注册任务可切换状态、列表回显，无权限用户被拒绝。 */
class AdminScheduledTaskToggleIntegrationTest extends AdminApiTestSupport {

    @Test
    void featureRolloutConsumedByAppFeatures() throws Exception {
        String admin = login("admin");
        JsonNode flags = ok(getJson("/api/admin/system/features", admin, Map.of()));
        assertTrue(flags.size() > 0, "应存在功能开关");
        String key = null;
        for (JsonNode flag : flags) {
            if (flag.path("enabled").asBoolean()) {
                key = flag.path("key").asText();
                break;
            }
        }
        assertTrue(key != null, "应至少有一个默认开启的功能开关");

        ok(call(post("/api/admin/system/features/" + key + "/rollout")
                .param("campusId", "0").param("percent", "0")
                .header("Authorization", "Bearer " + admin), 200));
        JsonNode appFlags = ok(getJson("/api/common/features", admin, Map.of()));
        assertFalse(appFlags.path("flags").path(key).asBoolean(), "灰度 0% 时当前用户应关闭该功能");

        ok(call(post("/api/admin/system/features/" + key + "/rollout")
                .param("campusId", "0").param("percent", "100")
                .header("Authorization", "Bearer " + admin), 200));
        JsonNode restored = ok(getJson("/api/common/features", admin, Map.of()));
        assertTrue(restored.path("flags").path(key).asBoolean(), "恢复 100% 后应重新开启");
    }

    @Test
    void toggleRegisteredTaskAndRejectUnauthorized() throws Exception {
        String admin = login("admin");
        String requester = login("requester");

        JsonNode tasks = ok(getJson("/api/admin/system/scheduled-tasks", admin, Map.of()));
        assertTrue(tasks.size() > 0, "应至少注册一个 @Scheduled 任务");
        String key = tasks.get(0).path("key").asText();
        assertTrue(tasks.get(0).path("enabled").asBoolean(), "任务默认应为启用");

        JsonNode disabled = ok(call(post("/api/admin/system/scheduled-tasks/toggle")
                .param("key", key).param("enabled", "false")
                .header("Authorization", "Bearer " + admin), 200));
        assertFalse(disabled.path("enabled").asBoolean(), "切换后返回 enabled=false");

        JsonNode afterDisable = ok(getJson("/api/admin/system/scheduled-tasks", admin, Map.of()));
        boolean foundDisabled = false;
        for (JsonNode task : afterDisable) {
            if (key.equals(task.path("key").asText())) {
                foundDisabled = !task.path("enabled").asBoolean();
            }
        }
        assertTrue(foundDisabled, "列表应回显停用状态: " + key);

        // 清理：恢复启用，避免污染其他测试
        ok(call(post("/api/admin/system/scheduled-tasks/toggle")
                .param("key", key).param("enabled", "true")
                .header("Authorization", "Bearer " + admin), 200));

        call(post("/api/admin/system/scheduled-tasks/toggle")
                .param("key", key).param("enabled", "false")
                .header("Authorization", "Bearer " + requester), 403);
    }
}
