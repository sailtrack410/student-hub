package com.studenthub.modules.push.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 推送模板变量渲染：把 {@code {变量名}} 替换为调用方传入的值。
 *
 * <p>设计取舍：缺失变量不静默留空（避免推到用户面前出现「{studentName} 同学」这类脏数据），
 * 而是由 {@link RenderResult#missing()} 上报，由 Service 转 400。</p>
 */
public final class PushTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]{1,64})}");

    private PushTemplateRenderer() {
    }

    /** 渲染结果；missing 非空表示模板需要的变量没有提供。 */
    public record RenderResult(String text, List<String> missing) {
        public boolean ok() {
            return missing.isEmpty();
        }
    }

    /** 提取模板中声明的变量名（去重保序）。 */
    public static List<String> variables(String template) {
        if (template == null || template.isBlank()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return List.copyOf(names);
    }

    /** 渲染单个模板串。 */
    public static RenderResult render(String template, Map<String, String> values) {
        if (template == null) {
            return new RenderResult("", List.of());
        }
        List<String> missing = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder builder = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            builder.append(template, last, matcher.start());
            String name = matcher.group(1);
            String value = values == null ? null : values.get(name);
            if (value == null || value.isEmpty()) {
                missing.add(name);
                builder.append(matcher.group(0));
            } else {
                builder.append(value);
            }
            last = matcher.end();
        }
        builder.append(template.substring(last));
        return new RenderResult(builder.toString(), List.copyOf(new LinkedHashSet<>(missing)));
    }

    /** 同时渲染标题与正文，合并缺失变量。 */
    public static RenderResult render(String title, String content, Map<String, String> values) {
        RenderResult titleResult = render(title, values);
        RenderResult contentResult = render(content, values);
        Set<String> missing = new LinkedHashSet<>(titleResult.missing());
        missing.addAll(contentResult.missing());
        // 标题/正文分别渲染后各自保留占位符；这里只做缺失汇总，避免交叉替换。
        return new RenderResult(titleResult.text() + "\u0000" + contentResult.text(), List.copyOf(missing));
    }
}
