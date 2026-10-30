import 'api_client.dart';

class ManagedDevice {
  final String deviceId;
  final String deviceName;
  final String osType;
  final String osVersion;
  final String ipAddress;
  final bool isCurrent;
  final DateTime? lastActiveAt;

  const ManagedDevice({
    required this.deviceId,
    required this.deviceName,
    this.osType = '',
    this.osVersion = '',
    this.ipAddress = '',
    this.isCurrent = false,
    this.lastActiveAt,
  });

  factory ManagedDevice.fromJson(Map<String, dynamic> json) {
    return ManagedDevice(
      deviceId: json['deviceId']?.toString() ?? '',
      deviceName: json['deviceName']?.toString() ?? '未知设备',
      osType: json['osType']?.toString() ?? '',
      osVersion: json['osVersion']?.toString() ?? '',
      ipAddress: json['ipAddress']?.toString() ?? '',
      isCurrent: json['isCurrent'] == true,
      lastActiveAt: DateTime.tryParse(json['lastActiveAt']?.toString() ?? ''),
    );
  }

  String get displayName {
    final system = [
      osType,
      osVersion,
    ].where((e) => e.isNotEmpty && e != '其他').join(' ');
    return system.isEmpty ? deviceName : '$deviceName · $system';
  }
}

class DeviceApi {
  static final ApiClient _client = ApiClient();

  static Future<List<ManagedDevice>> fetchDevices() async {
    try {
      final res = await _client.get('/api/app/devices');
      final data = res is List
          ? res
          : (res is Map<String, dynamic> && res['data'] is List)
          ? res['data'] as List
          : const [];
      return data
          .whereType<Map<String, dynamic>>()
          .map(ManagedDevice.fromJson)
          .toList();
    } catch (_) {
      return const [];
    }
  }

  static Future<bool> kickDevice(String deviceId) async {
    try {
      await _client.delete('/api/app/devices/$deviceId');
      return true;
    } catch (_) {
      return false;
    }
  }

  static Future<int> logoutOthers() async {
    try {
      final res = await _client.post(
        '/api/app/devices/logout-others',
        body: {},
      );
      final data = res is Map<String, dynamic>
          ? res
          : const <String, dynamic>{};
      final count =
          data['revokedCount'] ??
          (data['data'] is Map ? (data['data'] as Map)['revokedCount'] : null);
      return count is num ? count.toInt() : 0;
    } catch (_) {
      return 0;
    }
  }
}
