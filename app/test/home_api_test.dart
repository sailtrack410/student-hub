import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/models/home_dashboard_model.dart';

void main() {
  group('HomeDashboardModel Tests', () {
    test('parses full backend dashboard payload correctly', () {
      final json = {
        'greeting': '午安，调试同学',
        'academicInfo': {
          'campusName': '游仙校区',
          'weekDay': '周四',
          'academicWeek': 8,
          'formattedDate': '游仙校区 · 周四 第8周',
        },
        'nextSchedule': {
          'courseName': '软件工程导论',
          'teacher': '陈教授',
          'location': '明德楼 A302',
          'startSection': 3,
          'endSection': 4,
          'timeRange': '10:00-11:35',
          'status': 'ONGOING',
        },
        'announcement': {
          'id': '1024',
          'title': '【通知】图书馆关于期末延长开放时间的通知',
          'tag': '通知',
          'url': '/pages/announcement/detail?id=1024',
        },
        'inProgressRequest': {
          'requestId': '9901',
          'orderNo': 'WO-20261018-001',
          'itemName': '宿舍设施故障报修',
          'currentStatus': 'PROCESSING',
          'submittedAt': '2026-10-18 10:30',
        },
        'unreadMessageCount': '3',
      };

      final data = HomeDashboardData.fromJson(json);

      expect(data.greeting, '午安，调试同学');
      expect(data.academicInfo?.campusName, '游仙校区');
      expect(data.academicInfo?.academicWeek, 8);
      expect(data.academicInfo?.formattedDate, '游仙校区 · 周四 第8周');

      expect(data.nextSchedule?.courseName, '软件工程导论');
      expect(data.nextSchedule?.teacher, '陈教授');
      expect(data.nextSchedule?.location, '明德楼 A302');
      expect(data.nextSchedule?.isOngoing, isTrue);
      expect(data.nextSchedule?.displayText, '10:00-11:35 · 明德楼 A302');

      expect(data.announcement?.id, '1024');
      expect(data.announcement?.title, '【通知】图书馆关于期末延长开放时间的通知');
      expect(data.announcement?.tag, '通知');

      expect(data.inProgressRequest?.requestId, '9901');
      expect(data.inProgressRequest?.orderNo, 'WO-20261018-001');
      expect(data.inProgressRequest?.statusText, '处理中');

      expect(data.unreadMessageCount, 3);
    });

    test('handles null and partial backend dashboard payload safely', () {
      final json = <String, dynamic>{
        'greeting': null,
        'academicInfo': null,
        'nextSchedule': null,
        'announcement': null,
        'inProgressRequest': null,
        'unreadMessageCount': null,
      };

      final data = HomeDashboardData.fromJson(json);

      expect(data.greeting, '');
      expect(data.academicInfo, isNull);
      expect(data.nextSchedule, isNull);
      expect(data.announcement, isNull);
      expect(data.inProgressRequest, isNull);
      expect(data.unreadMessageCount, 0);
    });

    test('verifies inProgressRequest status text mappings', () {
      const statuses = {
        'SUBMITTED': '已提交',
        'PROCESSING': '处理中',
        'COMPLETED': '已办结',
        'REJECTED': '已驳回',
        'CANCELLED': '已撤销',
        'CUSTOM_STATUS': 'CUSTOM_STATUS',
      };

      for (final entry in statuses.entries) {
        final req = HomeInProgressRequestModel(
          requestId: '1',
          itemName: '测试',
          currentStatus: entry.key,
        );
        expect(req.statusText, entry.value);
      }
    });
  });
}
