package com.studenthub.modules.dict.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.dict.controller.dto.DictItemResponse;
import com.studenthub.modules.dict.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序-字典读取：按字典编码返回启用项（类型停用/不存在时返回空列表）。
 */
@RestController
@RequestMapping("/api/app/dicts")
@Tag(name = "小程序-字典", description = "按字典编码读取启用项")
@RequiredArgsConstructor
public class AppDictController {

    private final DictService dictService;

    @GetMapping("/{typeCode}")
    @Operation(summary = "读取字典项", description = "仅返回启用字典的启用项，按 sortOrder 升序；extra 为扩展 JSON 原文。",
            operationId = "getDictItems")
    public Result<List<DictItemResponse>> items(@PathVariable String typeCode) {
        return Result.success(dictService.enabledItems(typeCode));
    }
}
