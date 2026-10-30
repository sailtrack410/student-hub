import 'package:shared_preferences/shared_preferences.dart';

/// 用户协议 / 隐私政策同意记录。
///
/// - 登录成功后写入当前协议版本与同意时间，作为合规留痕；
/// - 协议内容更新时递增 [currentVersion]，可用 [hasCurrentConsent] 判断是否需要重新同意；
/// - 撤回同意只清除本机记录，不强制退出登录，下次登录时重新要求勾选。
class ConsentStore {
  ConsentStore._();

  /// 当前协议版本：协议内容调整上线时必须同步递增。
  static const String currentVersion = '2026-10-21';

  static const String versionKey = 'legal_consent_version';
  static const String agreedAtKey = 'legal_consent_at';

  /// 读取失败（插件未注册、测试环境）时按无记录处理，不阻断业务。
  static Future<SharedPreferences?> _prefs() async {
    try {
      return await SharedPreferences.getInstance().timeout(
        const Duration(seconds: 2),
      );
    } catch (_) {
      return null;
    }
  }

  /// 记录一次协议同意。写入失败不影响登录流程。
  static Future<void> recordConsent({String? version}) async {
    try {
      final prefs = await _prefs();
      if (prefs == null) return;
      await prefs.setString(versionKey, version ?? currentVersion);
      await prefs.setString(agreedAtKey, DateTime.now().toIso8601String());
    } catch (_) {
      // 同意记录仅用于合规留痕，失败不阻断登录。
    }
  }

  /// 已同意的协议版本；从未同意或读取失败时返回 null。
  static Future<String?> agreedVersion() async {
    try {
      final prefs = await _prefs();
      final version = prefs?.getString(versionKey)?.trim();
      return (version == null || version.isEmpty) ? null : version;
    } catch (_) {
      return null;
    }
  }

  /// 同意时间（ISO 8601 字符串）；未记录时返回 null。
  static Future<String?> agreedAt() async {
    try {
      final prefs = await _prefs();
      final at = prefs?.getString(agreedAtKey)?.trim();
      return (at == null || at.isEmpty) ? null : at;
    } catch (_) {
      return null;
    }
  }

  /// 是否已同意当前版本协议（协议升级后需重新同意）。
  static Future<bool> hasCurrentConsent() async =>
      (await agreedVersion()) == currentVersion;

  /// 撤回同意：清除本机同意记录，不退出登录。
  static Future<void> withdrawConsent() async {
    try {
      final prefs = await _prefs();
      await prefs?.remove(versionKey);
      await prefs?.remove(agreedAtKey);
    } catch (_) {
      // 清除失败时保持原记录，由调用方提示。
    }
  }
}
