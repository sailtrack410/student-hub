import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/models/homework_models.dart';

void main() {
  group('HomeworkSummary', () {
    test('parses backend payload with string id and submitted flag', () {
      final homework = HomeworkSummary.fromJson({
        'homeworkId': '2111000000000000001',
        'courseName': '软件工程导论',
        'title': '第三章课后作业',
        'teacherName': '张老师',
        'publishAt': '2026-10-19T08:00:00',
        'dueAt': '2099-10-30T18:00:00',
        'status': 'PUBLISHED',
        'submitted': true,
        'submittedAt': '2026-10-20T10:30:00',
      });

      expect(homework.homeworkId, '2111000000000000001');
      expect(homework.courseName, '软件工程导论');
      expect(homework.submitted, isTrue);
      expect(homework.submissionLabel, '已交');
      expect(homework.isClosed, isFalse);
      expect(homework.publishAt, DateTime(2026, 10, 19, 8));
      expect(homework.submittedAt, DateTime(2026, 10, 20, 10, 30));
    });

    test('labels unsubmitted overdue homework as 已截止', () {
      final homework = HomeworkSummary.fromJson({
        'homeworkId': '2',
        'courseName': '大学英语',
        'title': 'Unit 5 作文',
        'status': 'PUBLISHED',
        'submitted': false,
        'dueAt': '2000-01-01T00:00:00',
      });

      expect(homework.isOverdue, isTrue);
      expect(homework.submissionLabel, '已截止');
    });

    test('labels closed unsubmitted homework as 已关闭', () {
      final homework = HomeworkSummary.fromJson({
        'homeworkId': '3',
        'courseName': '高等数学',
        'title': '习题册',
        'status': 'CLOSED',
        'submitted': false,
      });

      expect(homework.isClosed, isTrue);
      expect(homework.submissionLabel, '已关闭');
      expect(homework.dueAt, isNull);
    });
  });

  group('HomeworkPageResult', () {
    test('parses pagination metadata and computes hasMore', () {
      final page = HomeworkPageResult.fromJson({
        'pageNum': '2',
        'pageSize': '10',
        'total': '12',
        'records': [
          {
            'homeworkId': '1',
            'courseName': '软件工程导论',
            'title': '作业一',
            'status': 'PUBLISHED',
            'submitted': false,
          },
          {
            'homeworkId': '2',
            'courseName': '软件工程导论',
            'title': '作业二',
            'status': 'PUBLISHED',
            'submitted': true,
          },
        ],
      });

      expect(page.pageNum, 2);
      expect(page.pageSize, 10);
      expect(page.total, 12);
      expect(page.records, hasLength(2));
      expect(page.hasMore, isTrue);
      expect(page.records[1].submitted, isTrue);
    });

    test('handles empty records safely', () {
      final page = HomeworkPageResult.fromJson({
        'pageNum': '1',
        'pageSize': '10',
        'total': '0',
        'records': <dynamic>[],
      });

      expect(page.records, isEmpty);
      expect(page.hasMore, isFalse);
    });
  });

  group('HomeworkDetail', () {
    test('keeps backend submittable flag but forces false when closed', () {
      final detail = HomeworkDetail.fromJson({
        'homeworkId': '9',
        'campusId': '1',
        'courseName': '计算机网络',
        'title': '实验报告',
        'content': '完成 Socket 编程实验并提交报告',
        'teacherName': '李老师',
        'dueAt': '2099-01-01T00:00:00',
        'status': 'CLOSED',
        'submittable': true,
        'submitted': false,
      });

      expect(detail.isClosed, isTrue);
      expect(detail.submittable, isFalse);
      expect(detail.content, contains('Socket'));
      expect(detail.submissionContent, isNull);
    });

    test('parses existing submission content and id', () {
      final detail = HomeworkDetail.fromJson({
        'homeworkId': '10',
        'campusId': '1',
        'courseName': '计算机网络',
        'title': '实验报告',
        'content': '实验要求',
        'status': 'PUBLISHED',
        'submittable': true,
        'submitted': true,
        'submissionId': '2111000000000000010',
        'submissionContent': '已完成实验，报告见附件说明。',
        'submittedAt': '2026-10-20T09:00:00',
      });

      expect(detail.submitted, isTrue);
      expect(detail.submissionId, '2111000000000000010');
      expect(detail.submissionContent, contains('已完成实验'));
      expect(detail.submittable, isTrue);
    });
  });
}
