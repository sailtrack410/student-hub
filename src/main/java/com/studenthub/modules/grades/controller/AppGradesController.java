package com.studenthub.modules.grades.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.grades.controller.dto.GradeReportResponse;
import com.studenthub.modules.grades.service.GradesService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序-学业成绩：实时读取教务「学业成绩」页，返回按学期分组的课程成绩与合计。
 */
@RestController
@RequestMapping("/api/app/grades")
@Tag(name = "小程序-学业成绩", description = "学业成绩查询（复用教务托管会话）")
@RequiredArgsConstructor
public class AppGradesController {

    private final GradesService gradesService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "查询学业成绩", description = "返回按学期分组的课程成绩（成绩/绩点/学分/学时/备注）与合计"
            + "（学分、GPA、平均成绩、加权平均成绩）。range=ALL|YEAR|TERM；scoreType=EFFECTIVE|RAW（缺省有效成绩）；"
            + "YEAR/TERM 模式可传 xn（学年起始年）与 xq（学期）。教务会话失效返回 JW_401_02。",
            operationId = "getGradeReport")
    public Result<GradeReportResponse> report(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String scoreType,
            @RequestParam(required = false) String xn,
            @RequestParam(required = false) String xq) {
        return Result.success(gradesService.report(
                currentUserProvider.requireCurrentUser(), range, scoreType, xn, xq));
    }
}
