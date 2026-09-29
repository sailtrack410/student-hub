import 'dart:ui' show ImageFilter;
import 'package:flutter/material.dart';
import '../theme/glass.dart';

/// 液态玻璃导航件（仅用于悬浮的导航/控件层）
class GlassNav extends StatelessWidget {
  const GlassNav({
    super.key,
    required this.child,
    this.borderRadius = 26,
    this.padding = const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
    this.tintOpacity = 0.42,
    this.blur = 14,
  });

  final Widget child;
  final double borderRadius;
  final EdgeInsetsGeometry padding;
  final double tintOpacity;
  final double blur;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      foregroundPainter: _EdgeHighlightPainter(borderRadius: borderRadius),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(borderRadius),
        child: BackdropFilter(
          filter: ImageFilter.compose(
            outer: const ColorFilter.matrix(<double>[
              1.2725, -0.2105, -0.018, 0, 0,
              -0.0795, 1.1295, -0.018, 0, 0,
              -0.0795, -0.2105, 1.29, 0, 0,
              0, 0, 0, 1, 0,
            ]),
            inner: ImageFilter.blur(sigmaX: blur, sigmaY: blur),
          ),
          child: Container(
            padding: padding,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Glass.deepTint.withValues(alpha: tintOpacity),
                  Glass.deepTint.withValues(alpha: tintOpacity * 0.55),
                ],
              ),
            ),
            child: child,
          ),
        ),
      ),
    );
  }
}

class _EdgeHighlightPainter extends CustomPainter {
  _EdgeHighlightPainter({required this.borderRadius});

  final double borderRadius;

  @override
  void paint(Canvas canvas, Size size) {
    final rrect = RRect.fromRectAndRadius(
      Offset.zero & size,
      Radius.circular(borderRadius),
    );
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.2
      ..shader = const LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          Color(0xD9FFFFFF),
          Color(0x2EFFFFFF),
          Color(0x14FFFFFF),
        ],
      ).createShader(Offset.zero & size);
    canvas.drawRRect(rrect.deflate(0.6), paint);
  }

  @override
  bool shouldRepaint(covariant _EdgeHighlightPainter old) =>
      old.borderRadius != borderRadius;
}
