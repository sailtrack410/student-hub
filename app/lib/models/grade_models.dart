class GradeReport {
  final String range;
  final String scoreType;
  final List<GradeTerm> terms;
  final GradeSummary summary;

  const GradeReport({
    this.range = 'ALL',
    this.scoreType = 'EFFECTIVE',
    this.terms = const [],
    required this.summary,
  });

  factory GradeReport.fromJson(Map<String, dynamic> json) {
    return GradeReport(
      range: json['range']?.toString() ?? 'ALL',
      scoreType: json['scoreType']?.toString() ?? 'EFFECTIVE',
      terms: ((json['terms'] as List?) ?? const [])
          .whereType<Map<String, dynamic>>()
          .map(GradeTerm.fromJson)
          .toList(),
      summary: GradeSummary.fromJson(
        json['summary'] is Map<String, dynamic>
            ? json['summary'] as Map<String, dynamic>
            : const {},
      ),
    );
  }
}

class GradeTerm {
  final String termName;
  final int courseCount;
  final double credits;
  final List<GradeCourse> courses;

  const GradeTerm({
    required this.termName,
    this.courseCount = 0,
    this.credits = 0,
    this.courses = const [],
  });

  factory GradeTerm.fromJson(Map<String, dynamic> json) {
    return GradeTerm(
      termName: json['termName']?.toString() ?? '学期',
      courseCount: int.tryParse('${json['courseCount']}') ?? 0,
      credits: double.tryParse('${json['credits']}') ?? 0,
      courses: ((json['courses'] as List?) ?? const [])
          .whereType<Map<String, dynamic>>()
          .map(GradeCourse.fromJson)
          .toList(),
    );
  }
}

class GradeCourse {
  final String courseName;
  final double credit;
  final String score;
  final double gradePoint;
  final String category;
  final String courseType;
  final bool passed;

  const GradeCourse({
    required this.courseName,
    this.credit = 0,
    this.score = '',
    this.gradePoint = 0,
    this.category = '',
    this.courseType = '',
    this.passed = true,
  });

  factory GradeCourse.fromJson(Map<String, dynamic> json) {
    return GradeCourse(
      courseName: json['courseName']?.toString() ?? '课程',
      credit: double.tryParse('${json['credit']}') ?? 0,
      score: json['score']?.toString() ?? '',
      gradePoint: double.tryParse('${json['gradePoint']}') ?? 0,
      category: json['category']?.toString() ?? '',
      courseType: json['courseType']?.toString() ?? '',
      passed:
          json['passed'] == true ||
          (double.tryParse('${json['earnedCredits']}') ?? 0) > 0,
    );
  }
}

class GradeSummary {
  final int courseCount;
  final double credits;
  final double earnedCredits;
  final double averageGradePoint;
  final double averageScore;

  const GradeSummary({
    this.courseCount = 0,
    this.credits = 0,
    this.earnedCredits = 0,
    this.averageGradePoint = 0,
    this.averageScore = 0,
  });

  factory GradeSummary.fromJson(Map<String, dynamic> json) {
    return GradeSummary(
      courseCount: int.tryParse('${json['courseCount']}') ?? 0,
      credits: double.tryParse('${json['credits']}') ?? 0,
      earnedCredits: double.tryParse('${json['earnedCredits']}') ?? 0,
      averageGradePoint: double.tryParse('${json['averageGradePoint']}') ?? 0,
      averageScore: double.tryParse('${json['averageScore']}') ?? 0,
    );
  }
}
