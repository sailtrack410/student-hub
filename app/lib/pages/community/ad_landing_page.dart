import 'dart:async';

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../api/community_api.dart';
import '../../theme/app_theme.dart';

/// 广告站内落地页（In-App WebView）
///
/// 仅用于可测落地类型（IN_APP_WEBVIEW / APP_PAGE）：
/// 1. 页面进入即开始计时；
/// 2. 首次加载完成上报 landing/loaded；
/// 3. 退出页面（返回/系统返回）上报 landing/exit，dwellMs 为进入页面到退出的毫秒数。
/// 非 http/https 链接、内嵌 WebView 初始化失败、加载失败均给出安全兜底，不崩溃。
class AdLandingPage extends StatefulWidget {
  final String clickId;
  final String targetUrl;
  final String title;
  final String brandName;

  const AdLandingPage({
    super.key,
    required this.clickId,
    required this.targetUrl,
    this.title = '',
    this.brandName = '',
  });

  /// 仅允许 http/https，拒绝 javascript:/file:/intent: 等危险 scheme
  static bool isSafeHttpUrl(String url) {
    final uri = Uri.tryParse(url);
    if (uri == null) return false;
    if (uri.scheme != 'http' && uri.scheme != 'https') return false;
    return uri.host.isNotEmpty;
  }

  @override
  State<AdLandingPage> createState() => _AdLandingPageState();
}

class _AdLandingPageState extends State<AdLandingPage> {
  WebViewController? _controller;
  late final DateTime _enteredAt;
  bool _loading = true;
  bool _error = false;
  bool _initFailed = false;
  bool _loadedReported = false;

  @override
  void initState() {
    super.initState();
    _enteredAt = DateTime.now();
    if (!AdLandingPage.isSafeHttpUrl(widget.targetUrl)) {
      _initFailed = true;
      _loading = false;
      return;
    }
    _initWebView();
  }

  void _initWebView() {
    try {
      final controller = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..setBackgroundColor(Colors.white)
        ..setNavigationDelegate(
          NavigationDelegate(
            onPageStarted: (_) {
              if (!mounted) return;
              setState(() => _loading = true);
            },
            onPageFinished: (_) {
              if (!mounted) return;
              setState(() {
                _loading = false;
                _error = false;
              });
              _reportLoadedOnce();
            },
            onWebResourceError: (error) {
              if (!mounted) return;
              // 主文档失败才显示错误态，子资源（图片/统计脚本）失败不阻断
              if (error.isForMainFrame ?? true) {
                setState(() {
                  _loading = false;
                  _error = true;
                });
              }
            },
          ),
        )
        ..loadRequest(Uri.parse(widget.targetUrl));
      if (!mounted) return;
      setState(() => _controller = controller);
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _initFailed = true;
        _loading = false;
      });
    }
  }

  void _reportLoadedOnce() {
    if (_loadedReported || widget.clickId.isEmpty) return;
    _loadedReported = true;
    unawaited(CommunityApi.reportLandingLoaded(clickId: widget.clickId));
  }

  @override
  void dispose() {
    if (_loadedReported && widget.clickId.isNotEmpty) {
      final dwellMs = DateTime.now().difference(_enteredAt).inMilliseconds;
      unawaited(
        CommunityApi.reportLandingExit(
          clickId: widget.clickId,
          dwellMs: dwellMs,
        ),
      );
    }
    super.dispose();
  }

  Future<void> _openExternally() async {
    final uri = Uri.tryParse(widget.targetUrl);
    if (uri == null) return;
    try {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('无法打开该链接')));
    }
  }

  void _retry() {
    setState(() {
      _error = false;
      _loading = true;
    });
    _controller?.reload();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        scrolledUnderElevation: 0,
        surfaceTintColor: Colors.transparent,
        leading: IconButton(
          icon: const Icon(
            Icons.arrow_back_ios_new_rounded,
            size: 18,
            color: AppColors.textPrimary,
          ),
          tooltip: '返回社区',
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: Text(
          widget.title.isEmpty ? '广告详情' : widget.title,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 16,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        actions: [
          if (widget.brandName.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(right: 14),
              child: Center(
                child: Text(
                  widget.brandName,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 12,
                    color: Color(0xFF92400E),
                  ),
                ),
              ),
            ),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_initFailed) {
      return _buildUnavailableState();
    }
    final controller = _controller;
    if (controller == null) {
      return const Center(
        child: SizedBox(
          width: 24,
          height: 24,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      );
    }
    return Stack(
      children: [
        Positioned.fill(child: WebViewWidget(controller: controller)),
        if (_loading)
          const Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: LinearProgressIndicator(minHeight: 2),
          ),
        if (_error) Positioned.fill(child: _buildLoadErrorState()),
      ],
    );
  }

  Widget _buildLoadErrorState() {
    return Container(
      color: Colors.white,
      alignment: Alignment.center,
      padding: const EdgeInsets.symmetric(horizontal: 40),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(
            Icons.cloud_off_rounded,
            size: 44,
            color: Color(0xFF94A3B8),
          ),
          const SizedBox(height: 14),
          const Text(
            '页面加载失败',
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 6),
          const Text(
            '广告页面暂时无法访问，请重试或返回社区',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              color: Color(0xFF64748B),
            ),
          ),
          const SizedBox(height: 18),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              OutlinedButton(
                onPressed: _retry,
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.brand,
                  side: const BorderSide(color: AppColors.brand),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(18),
                  ),
                ),
                child: const Text('重新加载'),
              ),
              const SizedBox(width: 12),
              OutlinedButton(
                onPressed: _openExternally,
                style: OutlinedButton.styleFrom(
                  foregroundColor: const Color(0xFF64748B),
                  side: const BorderSide(color: Color(0xFFCBD5E1)),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(18),
                  ),
                ),
                child: const Text('浏览器打开'),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildUnavailableState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 40),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.link_off_rounded,
              size: 44,
              color: Color(0xFF94A3B8),
            ),
            const SizedBox(height: 14),
            const Text(
              '该广告链接暂不支持 App 内打开',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 15,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 6),
            const Text(
              '仅支持 http/https 安全链接',
              style: TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                color: Color(0xFF64748B),
              ),
            ),
            const SizedBox(height: 18),
            OutlinedButton(
              onPressed: () => Navigator.of(context).maybePop(),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.brand,
                side: const BorderSide(color: AppColors.brand),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(18),
                ),
              ),
              child: const Text('返回社区'),
            ),
          ],
        ),
      ),
    );
  }
}
