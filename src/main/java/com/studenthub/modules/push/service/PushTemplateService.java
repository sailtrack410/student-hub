package com.studenthub.modules.push.service;

import com.studenthub.modules.push.controller.dto.PushTemplateRequest;
import com.studenthub.modules.push.controller.dto.PushTemplateResponse;
import com.studenthub.modules.push.controller.dto.PushTemplateTestRequest;
import com.studenthub.modules.push.controller.dto.PushTemplateTestResponse;

import java.util.List;

/**
 * 管理端推送模板：CRUD + 测试发送（发往当前管理员）。
 */
public interface PushTemplateService {

    List<PushTemplateResponse> list(String keyword, String scene, String status);

    PushTemplateResponse create(PushTemplateRequest request);

    PushTemplateResponse update(Long id, PushTemplateRequest request);

    void delete(Long id);

    /** 渲染变量并发往当前登录管理员（站内通知）+ 已配置通道。 */
    PushTemplateTestResponse test(Long id, PushTemplateTestRequest request);
}
