package com.studenthub.modules.screen.service;

import com.studenthub.modules.screen.controller.dto.PublicChatResponse;
import com.studenthub.modules.screen.controller.dto.ScreenCatalogResponse;
import com.studenthub.modules.screen.controller.dto.ScreenOrderProgressResponse;
import com.studenthub.modules.screen.controller.dto.ScreenOverviewResponse;

import java.util.List;

/**
 * 公开大屏服务：只读聚合真实运营数据 + 公开互动能力（目录浏览 / 单号查进度 / AI 问答），输出已脱敏。
 */
public interface ScreenService {

    ScreenOverviewResponse overview();

    ScreenCatalogResponse catalog();

    /** 单号支持工单号（WO-*）与申请单号（SR-*） */
    ScreenOrderProgressResponse track(String no);

    PublicChatResponse chat(String question, List<ScreenService.ChatTurn> history);

    record ChatTurn(String role, String content) {
    }
}