import 'dart:async';

import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// 社区动态多功能底部互动组件（点赞/评论/收藏/转发）
/// V4 画布 30:147 口径：条高 20，图标 20 + 文字 12 紧排，四组横跨全宽均布；
/// 非激活色 #94A3B8，点赞激活品红、收藏激活品牌蓝；分享为纯图标（30:241）。
///
/// A1 pending 去跳变（2026-11-05）：
/// - 点击瞬间图标不消失，仅做一次 240ms 微缩脉冲（120 下压 + 120 回弹）；
/// - 300ms 未返回才以 120ms easeOutCubic 淡入小 spinner（等长淡出）；
/// - disableAnimations 全程静默：无脉冲、无 spinner。
/// A2/A3/A4 动效（2026-11-05）：
/// - 激活瞬间（乐观更新同帧）弹 burst 1→1.25→1（各 100ms），替代本次按压 dip；
/// - 点赞/收藏图标翻转做 120ms 交叉淡化，不再瞬跳；
/// - 计数文本变化做 200ms 上滑淡入滚动。
class CommunityPostActionBar extends StatelessWidget {
  final int shareCount;
  final int commentCount;
  final int likeCount;
  final bool isLiked;
  final bool isBookmarked;
  final VoidCallback? onShare;
  final VoidCallback? onComment;
  final VoidCallback? onBookmark;
  final VoidCallback? onLike;
  final bool showBookmark;
  final bool likePending;
  final bool bookmarkPending;
  final double iconSize;
  final double fontSize;

  const CommunityPostActionBar({
    super.key,
    required this.shareCount,
    required this.commentCount,
    required this.likeCount,
    this.isLiked = false,
    this.isBookmarked = false,
    this.onShare,
    this.onComment,
    this.onBookmark,
    this.onLike,
    this.showBookmark = true,
    this.likePending = false,
    this.bookmarkPending = false,
    this.iconSize = 20,
    this.fontSize = 12,
  });

  @override
  Widget build(BuildContext context) {
    final defaultColor = AppColors.textTertiary;
    final bookmarkLabel = isBookmarked ? '已收藏' : '收藏';

    return SizedBox(
      width: double.infinity,
      child: Wrap(
        alignment: WrapAlignment.spaceBetween,
        spacing: 8,
        children: [
          CommunityActionEntry(
            label: likeCount > 0 ? '$likeCount' : '点赞',
            semantics: '${isLiked ? '取消点赞' : '点赞'}，$likeCount 人点赞',
            icon: isLiked
                ? Icons.favorite_rounded
                : Icons.favorite_border_rounded,
            color: isLiked ? AppColors.likeActive : defaultColor,
            onTap: onLike,
            selected: isLiked,
            pending: likePending,
            iconSize: iconSize,
            fontSize: fontSize,
          ),
          CommunityActionEntry(
            label: commentCount > 0 ? '$commentCount' : '评论',
            semantics: '评论，$commentCount 条',
            icon: Icons.chat_bubble_outline_rounded,
            color: defaultColor,
            onTap: onComment,
            iconSize: iconSize,
            fontSize: fontSize,
          ),
          if (showBookmark)
            CommunityActionEntry(
              // key 挂在内部 Semantics 上（community-bookmark），供测试与无障碍定位
              semanticsKey: const ValueKey('community-bookmark'),
              semantics: bookmarkLabel,
              icon: isBookmarked
                  ? Icons.bookmark_rounded
                  : Icons.bookmark_border_rounded,
              color: isBookmarked ? AppColors.brand : defaultColor,
              onTap: onBookmark,
              selected: isBookmarked,
              pending: bookmarkPending,
              iconSize: iconSize,
              fontSize: fontSize,
            ),
          CommunityActionEntry(
            semantics: '转发，$shareCount 次',
            icon: Icons.share_outlined,
            color: defaultColor,
            onTap: onShare,
            iconSize: iconSize,
            fontSize: fontSize,
          ),
        ],
      ),
    );
  }
}

/// 单个互动动作：持有自己的 spinner 延迟计时与一次性脉冲动画。
/// 公开给帖子详情页底部互动栏复用（信息流与详情同一动效语言）。
class CommunityActionEntry extends StatefulWidget {
  const CommunityActionEntry({
    super.key,
    this.semanticsKey,
    this.label,
    required this.semantics,
    required this.icon,
    required this.color,
    required this.onTap,
    this.iconSize = 20,
    this.fontSize = 12,
    this.selected,
    this.pending = false,
  });

  final Key? semanticsKey;
  final String? label;
  final String semantics;
  final IconData icon;
  final Color color;
  final VoidCallback? onTap;
  final bool? selected;
  final bool pending;
  final double iconSize;
  final double fontSize;

  @override
  State<CommunityActionEntry> createState() => _CommunityActionEntryState();
}

class _CommunityActionEntryState extends State<CommunityActionEntry>
    with TickerProviderStateMixin {
  static const _spinnerDelay = Duration(milliseconds: 300);
  static const _fade = Duration(milliseconds: 120);
  static const _roll = Duration(milliseconds: 200);
  static const _burstLeg = Duration(milliseconds: 100);

  Timer? _spinnerTimer;
  bool _spinnerVisible = false;
  late final AnimationController _pulse = AnimationController(
    vsync: this,
    duration: _fade,
    lowerBound: 0.92,
    upperBound: 1,
    value: 1,
  );
  // A2/A4 burst：激活瞬间弹一次 1→1.25→1（上扬与回落各 100ms）。
  late final AnimationController _burst = AnimationController(
    vsync: this,
    duration: _burstLeg,
    lowerBound: 1,
    upperBound: 1.25,
  );

  @override
  void didUpdateWidget(CommunityActionEntry oldWidget) {
    super.didUpdateWidget(oldWidget);
    final reduced = MediaQuery.disableAnimationsOf(context);
    final becameSelected =
        widget.selected == true && oldWidget.selected != true;
    if (becameSelected && !reduced) {
      // 激活瞬间（乐观更新同帧）弹 burst，替代本次的按压 dip。
      _burst.forward(from: 1).whenComplete(() {
        if (mounted) _burst.reverse();
      });
    } else if (widget.pending && !oldWidget.pending) {
      if (!reduced) {
        // 一次性脉冲：下压 120ms（1→0.92）后回弹 120ms（→1），结束停在 1。
        _pulse.reverse(from: 1).whenComplete(() {
          if (mounted) _pulse.forward();
        });
      }
    }
    if (widget.pending && !oldWidget.pending) {
      if (!reduced) {
        _spinnerTimer?.cancel();
        _spinnerTimer = Timer(_spinnerDelay, () {
          if (mounted && widget.pending) {
            setState(() => _spinnerVisible = true);
          }
        });
      }
    } else if (!widget.pending && oldWidget.pending) {
      _spinnerTimer?.cancel();
      if (_spinnerVisible) {
        setState(() => _spinnerVisible = false);
      }
    }
  }

  @override
  void dispose() {
    _spinnerTimer?.cancel();
    _pulse.dispose();
    _burst.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final reduced = MediaQuery.disableAnimationsOf(context);
    return Semantics(
      key: widget.semanticsKey,
      label: widget.pending ? '${widget.semantics}，正在保存' : widget.semantics,
      button: true,
      toggled: widget.selected,
      enabled: widget.onTap != null && !widget.pending,
      child: ExcludeSemantics(
        child: InkWell(
          onTap: widget.pending ? null : widget.onTap,
          borderRadius: BorderRadius.circular(8),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              ScaleTransition(
                scale: _pulse,
                child: ScaleTransition(
                  scale: _burst,
                  child: AnimatedSwitcher(
                    duration: reduced ? Duration.zero : _fade,
                    switchInCurve: Curves.easeOutCubic,
                    switchOutCurve: Curves.easeOutCubic,
                    child: _spinnerVisible
                        ? SizedBox(
                            key: const ValueKey('entry-spinner'),
                            width: widget.iconSize,
                            height: widget.iconSize,
                            child: CircularProgressIndicator(
                              strokeWidth: 1.5,
                              color: widget.color,
                            ),
                          )
                        : Icon(
                            // 键含图标与颜色：激活态翻转时做 120ms 交叉淡化
                            key: ValueKey(
                              'entry-icon-${widget.icon}-${widget.color.toARGB32()}',
                            ),
                            widget.icon,
                            size: widget.iconSize,
                            color: widget.color,
                          ),
                  ),
                ),
              ),
              if (widget.label != null) ...[
                const SizedBox(width: 5),
                // A3 计数滚动：数值变化 200ms 上滑淡入/下滑淡出
                AnimatedSwitcher(
                  duration: reduced ? Duration.zero : _roll,
                  switchInCurve: Curves.easeOutCubic,
                  switchOutCurve: Curves.easeOutCubic,
                  transitionBuilder: (child, animation) => FadeTransition(
                    opacity: animation,
                    child: SlideTransition(
                      position: Tween<Offset>(
                        begin: const Offset(0, 0.4),
                        end: Offset.zero,
                      ).animate(animation),
                      child: child,
                    ),
                  ),
                  child: Text(
                    widget.label!,
                    key: ValueKey(widget.label!),
                    style: TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: widget.fontSize < 12 ? 12 : widget.fontSize,
                      color: widget.color,
                      fontWeight: widget.selected == true
                          ? FontWeight.bold
                          : FontWeight.normal,
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
