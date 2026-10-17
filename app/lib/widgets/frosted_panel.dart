import 'package:flutter/material.dart';
import '../theme/glass.dart';

/// 磨砂玻璃容器：blur(24) + saturate(1.4) + 渐变填充 + 折射渐变描边 + 双层投影
class FrostedPanel extends StatelessWidget {
  const FrostedPanel({
    super.key,
    required this.child,
    this.borderRadius = 31,
    this.padding,
    this.fillTop = Glass.fillTop,
    this.fillBottom = Glass.fillBottom,
  });

  final Widget child;
  final double borderRadius;
  final EdgeInsetsGeometry? padding;
  final Color fillTop;
  final Color fillBottom;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // 双层投影
        Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(borderRadius),
            boxShadow: const [
              BoxShadow(
                color: Glass.shadowAmbient,
                offset: Offset(0, 16),
                blurRadius: 40,
              ),
              BoxShadow(
                color: Glass.shadowContact,
                offset: Offset(0, 4),
                blurRadius: 10,
              ),
            ],
          ),
        ),
        // 渐变描边层
        Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(borderRadius),
            gradient: const LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                Glass.strokeTop,
                Glass.strokeMid,
                Glass.strokeBottom,
              ],
            ),
          ),
          padding: const EdgeInsets.all(1.5),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(borderRadius - 1.5),
            child: BackdropFilter(
              filter: Glass.frosted,
              child: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [fillTop, fillBottom],
                  ),
                ),
                child: padding != null
                    ? Padding(padding: padding!, child: child)
                    : child,
              ),
            ),
          ),
        ),
      ],
    );
  }
}
