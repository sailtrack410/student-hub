package com.studenthub.modules.push.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推送模板变量渲染测试：占位符提取、正常替换、缺失变量上报。
 *
 * <p>缺失变量必须显式上报（由 Service 转 400），不能在推送文案里残留 {变量}，
 * 否则用户会收到脏数据。这里锁定该行为。</p>
 */
class PushTemplateRendererTest {

    @Test
    void shouldExtractVariablesInOrderWithoutDuplicates() {
        List<String> variables = PushTemplateRenderer.variables(
                "{name} 同学，工单 {orderNo} 已完工；{name} 可在 {orderNo} 详情查看");
        assertEquals(List.of("name", "orderNo"), variables);
    }

    @Test
    void shouldRenderAllPlaceholders() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", "李同学");
        values.put("worker", "王师傅");

        PushTemplateRenderer.RenderResult result =
                PushTemplateRenderer.render("{name} 你好，{worker} 已上门", values);

        assertTrue(result.ok());
        assertEquals("李同学 你好，王师傅 已上门", result.text());
    }

    @Test
    void shouldReportMissingVariablesAndKeepPlaceholder() {
        PushTemplateRenderer.RenderResult result =
                PushTemplateRenderer.render("{name} 的工单 {orderNo} 已完工", Map.of("name", "李同学"));

        assertFalse(result.ok());
        assertEquals(List.of("orderNo"), result.missing());
        assertTrue(result.text().contains("{orderNo}"));
    }

    @Test
    void shouldTreatEmptyValueAsMissing() {
        PushTemplateRenderer.RenderResult result =
                PushTemplateRenderer.render("你好 {name}", Map.of("name", ""));

        assertFalse(result.ok());
        assertEquals(List.of("name"), result.missing());
    }

    @Test
    void shouldReturnEmptyForNullTemplate() {
        PushTemplateRenderer.RenderResult result = PushTemplateRenderer.render(null, Map.of());
        assertTrue(result.ok());
        assertEquals("", result.text());
        assertEquals(List.of(), PushTemplateRenderer.variables(null));
    }
}
