import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/feature_flags.dart';
import '../../theme/app_theme.dart';

/// AI 助手停用页：由 `feature.ai` 开关驱动（管理端「功能开关」）。
///
/// 行为约定（真实降级）：
/// - 开关关闭时不构建 [AIAssistantPage]、不请求 `/api/app/ai/**`，直接展示停用说明；
/// - 不展示任何模拟对话或推荐指令，避免用户误以为功能可用；
/// - 「重新检测」会重新拉取开关，管理端重新打开后立即恢复，无需重启 App。
class AiDisabledPage extends StatefulWidget {
  const AiDisabledPage({super.key, this.currentTabIndex = 2});

  final int currentTabIndex;

  @override
  State<AiDisabledPage> createState() => _AiDisabledPageState();
}

class _AiDisabledPageState extends State<AiDisabledPage> {
  bool _checking = false;

  Future<void> _reload() async {
    if (_checking) return;
    setState(() => _checking = true);
    try {
      await FeatureFlags.load(ApiClient());
      await FeatureFlags.waitReady();
    } finally {
      if (mounted) setState(() => _checking = false);
    }
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(FeatureFlags.aiEnabled ? 'AI 助手已恢复，正在打开' : 'AI 助手仍在维护中'),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
        title: const Text(
          '绵宝AI',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
      ),
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 64,
                  height: 64,
                  decoration: BoxDecoration(
                    color: const Color(0xFFEFF6FF),
                    borderRadius: BorderRadius.circular(32),
                  ),
                  child: const Icon(
                    Icons.auto_awesome_outlined,
                    size: 30,
                    color: Color(0xFF2563EB),
                  ),
                ),
                const SizedBox(height: 18),
                const Text(
                  'AI 助手暂停服务',
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
                const SizedBox(height: 10),
                const Text(
                  '管理端已关闭 AI 助手功能，当前不可发起智能问答。你仍可使用服务大厅、报修、社区与课表等其它功能。',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 13,
                    height: 1.6,
                    color: Color(0xFF64748B),
                  ),
                ),
                const SizedBox(height: 20),
                SizedBox(
                  width: double.infinity,
                  height: 44,
                  child: ElevatedButton(
                    onPressed: _checking ? null : _reload,
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
                            '重新检测',
                            style: TextStyle(
                              fontFamily: 'Noto Sans SC',
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                  ),
                ),
                const SizedBox(height: 10),
                const Text(
                  '开关说明：管理端「系统 → 功能开关 → feature.ai」',
                  style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
