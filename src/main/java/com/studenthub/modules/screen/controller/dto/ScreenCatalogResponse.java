package com.studenthub.modules.screen.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 公开大屏 · 服务目录（分类 + 事项），仅含公开办理信息，不返回表单模板。
 */
@Schema(description = "公开服务目录")
public record ScreenCatalogResponse(List<Category> categories) {

    public record Category(Long id, String name, String icon, String description, List<Item> items) {
    }

    public record Item(Long id, String itemCode, String name, String icon, String summary,
                       String description, Integer slaHours) {
    }
}