import 'package:flutter/material.dart';

import '../models/community_models.dart';
import '../theme/app_theme.dart';

/// 官方直投广告卡片
///
/// 与普通帖子卡明显区分：琥珀色「推广/赞助」标识 + 品牌行 + CTA 按钮，
/// 不使用作者/等级/互动栏，避免伪装成社区帖子。
class CommunityAdCard extends StatelessWidget {
  final CommunityAd ad;
  final VoidCallback? onTap;
  final VoidCallback? onCtaTap;

  const CommunityAdCard({
    super.key,
    required this.ad,
    this.onTap,
    this.onCtaTap,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 10, 16, 10),
      decoration: BoxDecoration(
        color: const Color(0xFFFFFBEB),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFFDE68A)),
      ),
      clipBehavior: Clip.antiAlias,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildHeaderRow(),
                if ((ad.coverUrl ?? '').isNotEmpty) ...[
                  const SizedBox(height: 10),
                  _buildCover(),
                ],
                const SizedBox(height: 10),
                Text(
                  ad.title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontFamily: 'Noto Sans SC',
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                    height: 1.35,
                    color: AppColors.textPrimary,
                  ),
                ),
                if (ad.description.isNotEmpty) ...[
                  const SizedBox(height: 5),
                  Text(
                    ad.description,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontFamily: 'Noto Sans SC',
                      fontSize: 12,
                      height: 1.45,
                      color: Color(0xFF64748B),
                    ),
                  ),
                ],
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        ad.sponsoredLabel,
                        style: const TextStyle(
                          fontFamily: 'Noto Sans SC',
                          fontSize: 12,
                          color: Color(0xFFB45309),
                        ),
                      ),
                    ),
                    _buildCtaButton(),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeaderRow() {
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
          decoration: BoxDecoration(
            color: const Color(0xFFFDE68A),
            borderRadius: BorderRadius.circular(4),
          ),
          child: Text(
            ad.sponsoredLabel.isEmpty ? '推广' : ad.sponsoredLabel,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              fontWeight: FontWeight.bold,
              color: Color(0xFF92400E),
              height: 1.2,
            ),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            ad.brandName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontFamily: 'Noto Sans SC',
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: Color(0xFF92400E),
            ),
          ),
        ),
        const SizedBox(width: 8),
        const Text(
          '广告',
          style: TextStyle(
            fontFamily: 'Noto Sans SC',
            fontSize: 12,
            color: Color(0xFFB45309),
          ),
        ),
      ],
    );
  }

  Widget _buildCover() {
    return ClipRRect(
      borderRadius: BorderRadius.circular(10),
      child: Image.network(
        ad.coverUrl!,
        width: double.infinity,
        height: 132,
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => Container(
          width: double.infinity,
          height: 132,
          color: const Color(0xFFFEF3C7),
          alignment: Alignment.center,
          child: const Icon(
            Icons.image_outlined,
            size: 28,
            color: Color(0xFFD97706),
          ),
        ),
        loadingBuilder: (context, child, progress) {
          if (progress == null) return child;
          return Container(
            width: double.infinity,
            height: 132,
            color: const Color(0xFFFEF3C7),
            alignment: Alignment.center,
            child: const SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
          );
        },
      ),
    );
  }

  Widget _buildCtaButton() {
    return GestureDetector(
      onTap: onCtaTap ?? onTap,
      behavior: HitTestBehavior.opaque,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: AppColors.brand,
          borderRadius: BorderRadius.circular(14),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              ad.displayCta,
              style: const TextStyle(
                fontFamily: 'Noto Sans SC',
                fontSize: 12,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
            const SizedBox(width: 2),
            const Icon(
              Icons.chevron_right_rounded,
              size: 14,
              color: Colors.white,
            ),
          ],
        ),
      ),
    );
  }
}
