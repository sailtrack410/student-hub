import 'system_notification_stub.dart'
    if (dart.library.html) 'system_notification_web.dart'
    if (dart.library.io) 'system_notification_io.dart';

Future<void> showSystemNotification(String title, String body) =>
    showSystemNotificationImpl(title, body);
