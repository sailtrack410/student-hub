package com.studenthub.modules.grades.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学业成绩报表：按学期分组的课程成绩 + 表尾合计。
 * 字段与学校教务「学业成绩」页（xscj.stuckcj.jsp）逐列对应，也与竞品 grades 接口结构对齐。
 */
@Schema(description = "学业成绩报表")
public record GradeReportResponse(
        @Schema(description = "范围：ALL 入学以来 / YEAR 学年 / TERM 学期") String range,
        @Schema(description = "成绩类型：EFFECTIVE 有效成绩 / RAW 原始成绩") String scoreType,
        @Schema(description = "按学期分组") List<Term> terms,
        @Schema(description = "合计") Summary summary) {

    @Schema(description = "学期成绩")
    public record Term(
            @Schema(description = "学期名，如 2025-2026学年第一学期") String termName,
            @Schema(description = "课程门数") int courseCount,
            @Schema(description = "学期学分合计") BigDecimal credits,
            @Schema(description = "课程列表") List<Course> courses) {
    }

    @Schema(description = "单门课程成绩")
    public record Course(
            @Schema(description = "序号") Integer sequence,
            @Schema(description = "课程编码，如 M05H1023") String courseCode,
            @Schema(description = "课程名称") String courseName,
            @Schema(description = "学分") BigDecimal credit,
            @Schema(description = "总学时") Integer hours,
            @Schema(description = "类别（知识体系/能力体系路径）") String category,
            @Schema(description = "修读性质（初修/重修等）") String courseType,
            @Schema(description = "考核方式（考试/考查）") String examType,
            @Schema(description = "成绩（字符串保留等级制原值）") String score,
            @Schema(description = "获得学分") BigDecimal earnedCredits,
            @Schema(description = "绩点") BigDecimal gradePoint,
            @Schema(description = "学分绩点（绩点×学分）") BigDecimal creditGradePoint,
            @Schema(description = "备注（补考/重修等）") String remark,
            @Schema(description = "是否通过（获得学分>0）") boolean passed) {
    }

    /** 合计行：修读课程环节数/学分/获得学分/获得绩点/获得学分绩点/获得平均学分绩点/平均成绩/加权平均成绩 */
    @Schema(description = "成绩合计")
    public record Summary(
            @Schema(description = "修读课程环节数") Integer courseCount,
            @Schema(description = "学分合计") BigDecimal credits,
            @Schema(description = "获得学分合计") BigDecimal earnedCredits,
            @Schema(description = "获得绩点合计") BigDecimal earnedGradePoints,
            @Schema(description = "获得学分绩点合计") BigDecimal earnedCreditGradePoints,
            @Schema(description = "获得平均学分绩点（GPA）") BigDecimal averageGradePoint,
            @Schema(description = "平均成绩") BigDecimal averageScore,
            @Schema(description = "加权平均成绩") BigDecimal weightedAverageScore) {
    }
}
