import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/feature_flags.dart';
import '../../theme/app_theme.dart';

/// 平台维护页：由 `feature.maintenance` 开关驱动（管理端「功能开关」）。
///
/// 行为约定（真实降级，不伪造数据）：
/// - 开关未下发或请求失败时按默认「非维护」处理，避免误锁用户；
/// - 维护中不请求业务接口，只展示维护说明与刷新入口；
/// - 点击「刷新状态」重新拉取开关，管理端关闭维护后无需重启 App 即可恢复。
class MaintenancePage extends StatefulWidget {
  const MaintenancePage({super.key, this.onRetry});

  /// 外部刷新回调（由 [MaintenanceGate] 注入），返回是否仍处于维护中。
  final Future<bool> Function()? onRetry;

  @override
  State<MaintenancePage> createState() => _MaintenancePageState();
}

class _MaintenancePageState extends State<MaintenancePage> {
  bool _checking = false;
  DateTime? _lastCheckedAt;

  Future<void> _retry() async {
    if (_checking) return;
    setState(() => _checking = true);
    try {
      if (widget.onRetry != null) {
        await widget.onRetry!();
      } else {
        await FeatureFlags.load(ApiClient());
        await FeatureFlags.waitReady();
      }
    } finally {
      if (mounted) {
        setState(() {
          _checking = false;
          _lastCheckedAt = DateTime.now();
        });
      }
    }
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(FeatureFlags.maintenanceEnabled
              ? '平台仍在维护中，请稍后再试'
              : '维护已结束，正在恢复'),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 72,
                    height: 72,
                    decoration: BoxDecoration(
                      color: const Color(0xFFEFF6FF),
                      borderRadius: BorderRadius.circular(36),
                    ),
                    child: const Icon(
                      Icons.engineering_outlined,
                      size: 34,
                      color: Color(0xFF2563EB),
                    ),
                  ),
                  const SizedBox(height: 20),
                  const Text(
                    '系统维护中',
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF0F172A),
                    ),
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    '平台正在进行系统升级，选课、报修、社区等服务暂停使用，维护完成后将自动恢复。',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 13,
                      height: 1.6,
                      color: Color(0xFF64748B),
                    ),
                  ),
                  const SizedBox(height: 18),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: const Color(0xFFE2E8F0)),
                    ),
                    child: const Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _MaintenanceTip(text: '维护前已提交的申请与工单不会丢失，恢复后按原流程继续'),
                        SizedBox(height: 8),
                        _MaintenanceTip(text: '如遇紧急事项，请联系学校服务大厅或拨打后勤值班电话'),
                        SizedBox(height: 8),
                        _MaintenanceTip(text: '本页面由管理端「功能开关 · 维护模式」控制，关闭后立即恢复'),
                      ],
                    ),
                  ),
                  const SizedBox(height: 18),
                  SizedBox(
                    width: double.infinity,
                    height: 44,
                    child: ElevatedButton(
                      onPressed: _checking ? null : _retry,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.brand,
                        foregroundColor: Colors.white,
                        elevation: 0,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: _checking
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Text(
                              '刷新状态',
                              style: TextStyle(
                                fontFamily: 'Noto Sans SC',
                                fontSize: 14,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                    ),
                  ),
                  if (_lastCheckedAt != null) ...[
                    const SizedBox(height: 10),
                    Text(
                      '上次检查：${_lastCheckedAt!.hour.toString().padLeft(2, '0')}:'
                      '${_lastCheckedAt!.minute.toString().padLeft(2, '0')}:'
                      '${_lastCheckedAt!.second.toString().padLeft(2, '0')}',
                      style: const TextStyle(
                        fontSize: 12,
                        color: Color(0xFF64748B),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _MaintenanceTip extends StatelessWidget {
  const _MaintenanceTip({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.only(top: 5),
          child: SizedBox(
            width: 5,
            height: 5,
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: Color(0xFF94A3B8),
                shape: BoxShape.circle,
              ),
            ),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            text,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              height: 1.5,
              color: Color(0xFF475569),
            ),
          ),
        ),
      ],
    );
  }
}

/// 维护模式闸门：监听功能开关，`feature.maintenance=true` 时用维护页替换业务界面。
///
/// 放在 App 根部（HomePage 之外），保证维护期间不会发起业务请求。
class MaintenanceGate extends StatefulWidget {
  const MaintenanceGate({super.key, required this.child});

  final Widget child;

  @override
  State<MaintenanceGate> createState() => _MaintenanceGateState();
}

class _MaintenanceGateState extends State<MaintenanceGate> {
  bool _lastMaintenance = FeatureFlags.maintenanceEnabled;

  @override
  void initState() {
    super.initState();
    FeatureFlags.revision.addListener(_onFlagsChanged);
  }

  @override
  void dispose() {
    FeatureFlags.revision.removeListener(_onFlagsChanged);
    super.dispose();
  }

  void _onFlagsChanged() {
    if (!mounted) return;
    final current = FeatureFlags.maintenanceEnabled;
    if (current != _lastMaintenance) {
      setState(() => _lastMaintenance = current);
    }
  }

  Future<bool> _reload() async {
    await FeatureFlags.load(ApiClient());
    await FeatureFlags.waitReady();
    if (mounted) {
      setState(() => _lastMaintenance = FeatureFlags.maintenanceEnabled);
    }
    return FeatureFlags.maintenanceEnabled;
  }

  @override
  Widget build(BuildContext context) {
    if (_lastMaintenance) {
      return MaintenancePage(onRetry: _reload);
    }
    return widget.child;
  }
}
