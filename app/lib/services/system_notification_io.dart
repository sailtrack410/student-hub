Future<void> showSystemNotificationImpl(String title, String body) async {
  // 移动端系统推送需要 FCM/APNs Token，已由 /api/app/push/tokens 登记；
  // 前台实时通知走 WebSocket，系统级通知在接入 Provider 后由后端下发。
}
