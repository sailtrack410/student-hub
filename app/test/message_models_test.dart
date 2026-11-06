import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/models/message_models.dart';

void main() {
  test('notification ids accept long values serialized as strings', () {
    final n = CommunityNotificationItem.fromJson({
      'notificationId': '2118571337790509058',
      'relatedId': '3001',
      'read': false,
    });
    expect(n.notificationId, 2118571337790509058);
    expect(n.relatedId, 3001);
  });
}
