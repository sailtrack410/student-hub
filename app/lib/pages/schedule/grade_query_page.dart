import 'package:flutter/material.dart';

import '../../api/grade_api.dart';
import '../../models/grade_models.dart';

class _CourseGradeItem {
  final String courseName;
  final double credits;
  final double score;
  final double gpa;
  final String courseType; // 必修 / 选修 / 通识
  final bool isPassed;
  final String? scoreText;

  const _CourseGradeItem({
    required this.courseName,
    required this.credits,
    required this.score,
    required this.gpa,
    required this.courseType,
    this.isPassed = true,
    this.scoreText,
  });
}

/// APP-成绩与学业学分查询 (对应移动端功能说明 4.2 节，375 × 812)
class GradeQueryPage extends StatefulWidget {
  const GradeQueryPage({super.key});

  @override
  State<GradeQueryPage> createState() => _GradeQueryPageState();
}

class _GradeQueryPageState extends State<GradeQueryPage> {
  String _selectedSemester = '2025-2026学年 第二学期 (大二下)';
  List<String> _semesters = [
    '2025-2026学年 第二学期 (大二下)',
    '2025-2026学年 第一学期 (大二上)',
    '2024-2025学年 第二学期 (大一下)',
    '2024-2025学年 第一学期 (大一上)',
  ];

  GradeReport? _report;
  bool _demoMode = false;

  List<_CourseGradeItem> _grades = const [
    _CourseGradeItem(
      courseName: '高等数学 (下)',
      credits: 4.0,
      score: 92.0,
      gpa: 4.2,
      courseType: '专业必修',
    ),
    _CourseGradeItem(
      courseName: '软件工程导论',
      credits: 3.5,
      score: 95.0,
      gpa: 4.5,
      courseType: '核心必修',
    ),
    _CourseGradeItem(
      courseName: '数据库系统原理',
      credits: 3.0,
      score: 89.0,
      gpa: 3.9,
      courseType: '专业必修',
    ),
    _CourseGradeItem(
      courseName: '离散数学',
      credits: 3.0,
      score: 88.0,
      gpa: 3.8,
      courseType: '专业必修',
    ),
    _CourseGradeItem(
      courseName: '大学英语 (四)',
      credits: 2.0,
      score: 85.0,
      gpa: 3.5,
      courseType: '公共必修',
    ),
    _CourseGradeItem(
      courseName: '思想政治理论',
      credits: 2.5,
      score: 87.0,
      gpa: 3.7,
      courseType: '通识必修',
    ),
    _CourseGradeItem(
      courseName: '体育 (羽毛球)',
      credits: 1.0,
      score: 90.0,
      gpa: 4.0,
      courseType: '公共必修',
    ),
  ];

  @override
  void initState() {
    super.initState();
    _loadGrades();
  }

  Future<void> _loadGrades() async {
    final report = await GradeApi.fetchReport();
    if (!mounted) return;
    if (report == null || report.terms.isEmpty) {
      setState(() => _demoMode = true);
      return;
    }
    setState(() {
      _report = report;
      _semesters = report.terms.map((term) => term.termName).toList();
      _selectedSemester = _semesters.first;
      _applyTerm(report.terms.first);
    });
  }

  void _applyTerm(GradeTerm term) {
    _grades = term.courses.map((course) {
      final numericScore = double.tryParse(course.score) ?? 0;
      return _CourseGradeItem(
        courseName: course.courseName,
        credits: course.credit,
        score: numericScore,
        gpa: course.gradePoint,
        courseType: course.category.isNotEmpty
            ? course.category
            : (course.courseType.isNotEmpty ? course.courseType : '课程'),
        isPassed: course.passed,
        scoreText: course.score,
      );
    }).toList();
  }

  void _selectSemester(String? value) {
    if (value == null) return;
    final report = _report;
    setState(() {
      _selectedSemester = value;
      if (report != null) {
        for (final term in report.terms) {
          if (term.termName == value) {
            _applyTerm(term);
            break;
          }
        }
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final totalCredits =
        _report?.summary.earnedCredits ??
        _grades.fold<double>(0, (sum, g) => sum + g.credits);
    final weightedGpaSum = _grades.fold<double>(
      0,
      (sum, g) => sum + g.gpa * g.credits,
    );
    final avgGpa =
        _report?.summary.averageGradePoint.toStringAsFixed(2) ??
        (totalCredits == 0
            ? '0.00'
            : (weightedGpaSum /
                      _grades.fold<double>(0, (sum, g) => sum + g.credits))
                  .toStringAsFixed(2));
    final failedCount = _grades.where((g) => !g.isPassed).length;

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(
            Icons.arrow_back_ios_new_rounded,
            size: 20,
            color: Color(0xFF0F172A),
          ),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: const Text(
          '学期成绩与GPA',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
        centerTitle: true,
      ),
      body: Column(
        children: [
          // 1. 学期切换下拉条
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            color: Colors.white,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              decoration: BoxDecoration(
                color: const Color(0xFFF1F5F9),
                borderRadius: BorderRadius.circular(12),
              ),
              child: DropdownButtonHideUnderline(
                child: DropdownButton<String>(
                  value: _selectedSemester,
                  isExpanded: true,
                  icon: const Icon(
                    Icons.keyboard_arrow_down_rounded,
                    color: Color(0xFF64748B),
                  ),
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                  items: _semesters.map((s) {
                    return DropdownMenuItem(value: s, child: Text(s));
                  }).toList(),
                  onChanged: _selectSemester,
                ),
              ),
            ),
          ),

          if (_demoMode)
            Container(
              width: double.infinity,
              margin: const EdgeInsets.only(top: 8, left: 16, right: 16),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: const Color(0xFFFFFBEB),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: const Color(0xFFFDE68A)),
              ),
              child: const Text(
                '教务认证不可用，当前展示示例数据',
                style: TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 12,
                  color: Color(0xFF92400E),
                ),
              ),
            ),

          // 2. 成绩汇总看板卡
          Container(
            margin: const EdgeInsets.all(16),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [Color(0xFF2563EB), Color(0xFF1D4ED8)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(18),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x332563EB),
                  blurRadius: 16,
                  offset: Offset(0, 6),
                ),
              ],
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildSummaryCol('平均学分绩点', avgGpa, 'GPA'),
                Container(width: 1, height: 36, color: const Color(0x33FFFFFF)),
                _buildSummaryCol('已修学分', totalCredits.toStringAsFixed(1), '学分'),
                Container(width: 1, height: 36, color: const Color(0x33FFFFFF)),
                _buildSummaryCol('挂科科目', '$failedCount', '门通过'),
              ],
            ),
          ),

          // 3. 课程成绩列表
          Expanded(
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              itemCount: _grades.length,
              separatorBuilder: (_, _) => const SizedBox(height: 10),
              itemBuilder: (context, index) {
                final g = _grades[index];
                final isExcellent = g.score >= 90;

                return Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: Row(
                    children: [
                      // 左侧分数指示
                      Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: isExcellent
                              ? const Color(0xFFEFF6FF)
                              : const Color(0xFFF8FAFC),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(
                            color: isExcellent
                                ? const Color(0xFFBFDBFE)
                                : const Color(0xFFE2E8F0),
                          ),
                        ),
                        alignment: Alignment.center,
                        child: Text(
                          g.scoreText ?? g.score.toStringAsFixed(0),
                          style: TextStyle(
                            fontFamily: 'Noto Sans SC',
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                            color: isExcellent
                                ? const Color(0xFF2563EB)
                                : const Color(0xFF0F172A),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),

                      // 中间课程名称与性质
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              g.courseName,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 14,
                                fontWeight: FontWeight.bold,
                                color: Color(0xFF0F172A),
                              ),
                            ),
                            const SizedBox(height: 4),
                            Row(
                              children: [
                                Flexible(
                                  child: Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 5,
                                      vertical: 1,
                                    ),
                                    decoration: BoxDecoration(
                                      color: const Color(0xFFF1F5F9),
                                      borderRadius: BorderRadius.circular(4),
                                    ),
                                    child: Text(
                                      g.courseType,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(
                                        fontSize: 12,
                                        color: Color(0xFF64748B),
                                      ),
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 6),
                                Text(
                                  '${g.credits} 学分',
                                  style: const TextStyle(
                                    fontSize: 12,
                                    color: Color(0xFF64748B),
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),

                      // 右侧绩点与合格标记
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text(
                            '绩点 ${g.gpa.toStringAsFixed(1)}',
                            style: const TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 13,
                              fontWeight: FontWeight.bold,
                              color: Color(0xFF16A34A),
                            ),
                          ),
                          const SizedBox(height: 4),
                          const Text(
                            '正常通过',
                            style: TextStyle(
                              fontSize: 12,
                              color: Color(0xFF64748B),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSummaryCol(String label, String value, String unit) {
    return Column(
      children: [
        Text(
          label,
          style: const TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 12,
            color: Color(0xCCFFFFFF),
          ),
        ),
        const SizedBox(height: 4),
        Row(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.baseline,
          textBaseline: TextBaseline.alphabetic,
          children: [
            Text(
              value,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 22,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
            const SizedBox(width: 2),
            Text(
              unit,
              style: const TextStyle(fontSize: 12, color: Color(0xCCFFFFFF)),
            ),
          ],
        ),
      ],
    );
  }
}
