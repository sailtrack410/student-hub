import 'dart:html' as html;

Future<void> showSystemNotificationImpl(String title, String body) async {
  try {
    var permission = html.Notification.permission;
    if (permission != 'granted') {
      permission = await html.Notification.requestPermission();
    }
    if (permission == 'granted') {
      html.Notification(title, body: body, icon: '/icons/Icon-192.png');
    }
  } catch (_) {}
}
