import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// 异常状态提示条（严格对齐画布 APP-登录 4:33 节点）
/// 宽 327，高 38，圆角 10，底色 #FDF2F2，边框 #F9D1D1，居中排布
class ErrorToastBanner extends StatelessWidget {
  final String message;

  const ErrorToastBanner({super.key, required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: 48),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.dangerSoft,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.dangerBorder, width: 1),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          // 警告图标（红圈感叹号，对齐画布 4:34 节点）
          Container(
            width: 14,
            height: 14,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(color: AppColors.danger, width: 1.2),
            ),
            child: const Center(
              child: Text(
                '!',
                style: TextStyle(
                  color: AppColors.danger,
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  height: 1.0,
                ),
              ),
            ),
          ),
          const SizedBox(width: 8),
          // 提示文案（对齐画布 4:38 节点）
          Flexible(
            child: Text(
              message,
              style: const TextStyle(
                color: AppColors.danger,
                fontSize: 12,
                fontWeight: FontWeight.w400,
                height: 1.5,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
