import 'package:flutter/material.dart';

import '../../api/auth_service.dart';
import '../../api/profile_api.dart';
import '../../app_version.dart';
import '../../services/app_update_service.dart';
import '../../services/badge_icon_cache.dart';
import '../../services/consent_store.dart';
import '../legal/legal_document_content.dart';
import '../legal/legal_document_page.dart';

/// APP-设置与账号安全 (画布 5:1843)
class SettingsSecurityPage extends StatefulWidget {
  final AuthService authService;

  const SettingsSecurityPage({super.key, required this.authService});

  @override
  State<SettingsSecurityPage> createState() => _SettingsSecurityPageState();
}

class _SettingsSecurityPageState extends State<SettingsSecurityPage> {
  bool _loading = true;
  bool _showAcademicInfo = true;
  bool _showMedals = true;
  Map<String, dynamic>? _preferences;
  String? _consentVersion;
  final Set<String> _savingPreferences = {};
  bool _savingPrivacy = false;
  bool _clearingCache = false;
  int? _cacheBytes;

  @override
  void initState() {
    super.initState();
    _load();
    _refreshCacheSize();
  }

  Future<void> _load() async {
    String? consentVersion;
    try {
      consentVersion = await ConsentStore.agreedVersion().timeout(
        const Duration(seconds: 2),
      );
      final results = await Future.wait([
        ProfileApi.fetchPrivacy(),
        ProfileApi.fetchPreferences(),
      ]).timeout(const Duration(seconds: 8));
      if (!mounted) return;
      final privacy = results[0];
      final prefs = results[1];
      setState(() {
        _consentVersion = consentVersion;
        _preferences = prefs;
        _showAcademicInfo = privacy?['showAcademicInfo'] != false;
        _showMedals = privacy?['showMedals'] != false;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loading = false);
    }
  }

  bool _preference(String key) => key == 'chatReminder'
      ? (_preferences?[key] == true)
      : (_preferences?[key] != false);

  Future<void> _savePreference(String key, bool value) async {
    if (_savingPreferences.contains(key)) return;
    final previous = _preference(key);
    setState(() {
      _savingPreferences.add(key);
      _preferences = {...?_preferences, key: value};
    });
    Map<String, dynamic>? saved;
    try {
      saved = await ProfileApi.updatePreferences({key: value});
    } catch (_) {
      // 保留原值，统一处理网络或服务端保存失败。
    }
    if (!mounted) return;
    setState(() {
      _savingPreferences.remove(key);
      _preferences = {
        ...?_preferences,
        key: saved?[key] ?? (saved == null ? previous : value),
      };
    });
    if (saved == null) _toast('保存失败，已恢复原设置');
  }

  Future<void> _savePrivacy({bool? academic, bool? medals}) async {
    if (_savingPrivacy) return;
    final previousAcademic = _showAcademicInfo;
    final previousMedals = _showMedals;
    setState(() {
      _savingPrivacy = true;
      _showAcademicInfo = academic ?? _showAcademicInfo;
      _showMedals = medals ?? _showMedals;
    });
    Map<String, dynamic>? saved;
    try {
      saved = await ProfileApi.updatePrivacy(
        showAcademicInfo: _showAcademicInfo,
        showMedals: _showMedals,
      );
    } catch (_) {
      // 两个隐私字段一同提交，失败时一同恢复，避免并发覆盖。
    }
    if (!mounted) return;
    setState(() {
      _savingPrivacy = false;
      if (saved == null) {
        _showAcademicInfo = previousAcademic;
        _showMedals = previousMedals;
      }
    });
    if (saved == null) _toast('保存失败，已恢复原设置');
  }

  Future<void> _refreshCacheSize() async {
    int? bytes;
    try {
      bytes = await BadgeIconCache.diskUsageBytes();
    } catch (_) {
      // 无法读取时显示待统计，点击清理仍可重试。
    }
    if (mounted) setState(() => _cacheBytes = bytes);
  }

  String _formatCacheBytes(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  Future<void> _clearImageCache() async {
    if (_clearingCache) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('清除图片缓存'),
        content: const Text('清理已下载的勋章图片和内存图片，使用时会重新加载。登录状态、协议记录和下载的更新包会保留。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('清除'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _clearingCache = true);
    try {
      final removed = await BadgeIconCache.clear();
      PaintingBinding.instance.imageCache.clear();
      PaintingBinding.instance.imageCache.clearLiveImages();
      await _refreshCacheSize();
      if (mounted) _toast('已清除图片缓存（${_formatCacheBytes(removed)}）');
    } catch (_) {
      if (mounted) _toast('清理失败，请稍后重试');
    } finally {
      if (mounted) setState(() => _clearingCache = false);
    }
  }

  Future<void> _withdrawConsent() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        title: const Text(
          '撤回协议同意',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
        ),
        content: const Text(
          '撤回后将清除本机的协议同意记录，下次登录需要重新阅读并同意《用户协议》与《隐私政策》。'
          '当前登录状态不会退出。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('取消'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('确认撤回'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await ConsentStore.withdrawConsent();
    if (!mounted) return;
    setState(() => _consentVersion = null);
    _toast('已撤回，下次登录需重新同意');
  }

  Future<void> _checkVersion() async {
    final info = await AppUpdateService.check();
    if (!mounted) return;
    if (info == null) {
      _toast('版本检查失败，请检查网络后重试');
      return;
    }
    if (!info.hasUpdate) {
      _toast('当前已是最新版本 v$appVersionName');
      return;
    }
    if (info.latest == null) {
      _toast('暂未获取到版本信息，请稍后重试');
      return;
    }
    await AppUpdateService.prompt(context, info);
  }

  void _toast(String text) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F7F9),
      appBar: AppBar(
        backgroundColor: const Color(0xFFF6F7F9),
        elevation: 0,
        scrolledUnderElevation: 0,
        foregroundColor: const Color(0xFF20221F),
        title: const Text(
          '通用设置',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        ),
        centerTitle: true,
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(strokeWidth: 2))
          : ListView(
              padding: const EdgeInsets.fromLTRB(22, 12, 22, 30),
              children: [
                _buildSectionTitle('消息提醒'),
                _buildCard([
                  _buildPreferenceTile('私聊消息推送', 'chatPush'),
                  _buildPreferenceTile('应用内私聊提醒', 'chatReminder'),
                ]),
                const SizedBox(height: 14),
                _buildSectionTitle('工单提醒'),
                _buildCard([
                  _buildPreferenceTile('报修进度推送', 'repairPush'),
                  _buildPreferenceTile('工单状态服务号推送', 'workOrderPush'),
                ]),
                const SizedBox(height: 14),
                _buildSectionTitle('隐私与展示'),
                _buildCard([
                  _buildSwitchTile(
                    '社区发帖显示院系标签',
                    _showAcademicInfo,
                    _savingPrivacy
                        ? null
                        : (value) => _savePrivacy(academic: value),
                  ),
                  _buildSwitchTile(
                    '对他人展示荣誉勋章',
                    _showMedals,
                    _savingPrivacy
                        ? null
                        : (value) => _savePrivacy(medals: value),
                  ),
                ]),
                const SizedBox(height: 14),
                _buildSectionTitle('通用与关于'),
                _buildCard([
                  _buildLinkTile(
                    '清除图片缓存',
                    _clearingCache
                        ? '清理中…'
                        : (_cacheBytes == null
                              ? '待统计'
                              : _formatCacheBytes(_cacheBytes!)),
                    valueColor: const Color(0xFF94A3B8),
                    onTap: _clearingCache ? null : _clearImageCache,
                  ),
                  _buildLinkTile(
                    '检查新版本',
                    'v$appVersionName',
                    valueColor: const Color(0xFF94A3B8),
                    onTap: _checkVersion,
                  ),
                  _buildLinkTile(
                    '关于绵城校园',
                    '功能介绍与协议',
                    onTap: () => showAboutDialog(
                      context: context,
                      applicationName: '绵城校园',
                      applicationVersion: 'v$appVersionName',
                      children: const [
                        Text('绵城校园学生服务平台\n校园社区 · 服务办理 · 课表成绩 · 绵宝AI'),
                      ],
                    ),
                  ),
                ]),
                const SizedBox(height: 14),
                _buildSectionTitle('协议与隐私'),
                _buildCard([
                  _buildLinkTile(
                    '用户协议',
                    'v${ConsentStore.currentVersion}',
                    onTap: () => LegalDocumentPage.open(
                      context,
                      LegalDocumentType.userAgreement,
                    ),
                  ),
                  _buildLinkTile(
                    '隐私政策',
                    '',
                    onTap: () => LegalDocumentPage.open(
                      context,
                      LegalDocumentType.privacyPolicy,
                    ),
                  ),
                  _buildLinkTile(
                    '撤回协议同意',
                    _consentVersion == null ? '未记录' : '已同意 v$_consentVersion',
                    valueColor: _consentVersion == null
                        ? const Color(0xFF94A3B8)
                        : const Color(0xFF16A34A),
                    onTap: _withdrawConsent,
                  ),
                ]),
              ],
            ),
    );
  }

  Widget _buildSectionTitle(String title) => Padding(
    padding: const EdgeInsets.fromLTRB(4, 0, 4, 8),
    child: Text(
      title,
      style: const TextStyle(
        fontSize: 13,
        fontWeight: FontWeight.w600,
        color: Color(0xFF64748B),
      ),
    ),
  );

  Widget _buildPreferenceTile(String title, String key) => _buildSwitchTile(
    title,
    _preference(key),
    _savingPreferences.contains(key)
        ? null
        : (value) => _savePreference(key, value),
  );

  Widget _buildCard(List<Widget> children) {
    return Column(
      children: [
        for (int i = 0; i < children.length; i++) ...[
          if (i > 0) const Divider(height: 1, color: Color(0xFFDCDAD2)),
          children[i],
        ],
      ],
    );
  }

  Widget _buildLinkTile(
    String title,
    String value, {
    Color? valueColor,
    required VoidCallback? onTap,
  }) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        child: Row(
          children: [
            Expanded(
              child: Text(
                title,
                style: const TextStyle(
                  fontFamily: 'Noto Sans SC',
                  fontSize: 14,
                  color: Color(0xFF0F172A),
                ),
              ),
            ),
            if (value.isNotEmpty) ...[
              const SizedBox(width: 16),
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 150),
                child: Text(
                  value,
                  textAlign: TextAlign.right,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: valueColor ?? const Color(0xFF64748B),
                  ),
                ),
              ),
              const SizedBox(width: 8),
            ] else
              const SizedBox(width: 8),
            const Icon(
              Icons.chevron_right_rounded,
              size: 18,
              color: Color(0xFFCBD5E1),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSwitchTile(
    String title,
    bool value,
    ValueChanged<bool>? onChanged,
  ) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
      child: Row(
        children: [
          Expanded(
            child: Text(
              title,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 14,
                color: Color(0xFF0F172A),
              ),
            ),
          ),
          Switch(
            value: value,
            thumbColor: const WidgetStatePropertyAll(Colors.white),
            trackColor: WidgetStateProperty.resolveWith(
              (states) => states.contains(WidgetState.selected)
                  ? const Color(0xFF3478F6)
                  : const Color(0xFFD1D5DB),
            ),
            trackOutlineColor: const WidgetStatePropertyAll(Colors.transparent),
            onChanged: onChanged,
          ),
        ],
      ),
    );
  }
}
