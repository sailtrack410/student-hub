import 'package:flutter/material.dart';

import '../../../models/home_dashboard_model.dart';
import '../../../theme/app_theme.dart';

class HomeCourseCard extends StatelessWidget {
  final NextScheduleModel? course;
  final VoidCallback onOpen;

  const HomeCourseCard({super.key, this.course, required this.onOpen});

  @override
  Widget build(BuildContext context) {
    final current = course;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Semantics(
        button: true,
        label: current == null ? '我的校园日程，查看课表' : '查看完整课表',
        child: Material(
          color: Colors.white,
          borderRadius: BorderRadius.circular(24),
          elevation: 12,
          shadowColor: const Color(0x382563EB),
          child: InkWell(
            key: const ValueKey('home-course-card'),
            borderRadius: BorderRadius.circular(24),
            onTap: onOpen,
            child: Container(
              width: double.infinity,
              constraints: BoxConstraints(
                minHeight: current == null ? 168 : 148,
              ),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(24),
                border: Border.all(color: Colors.white.withValues(alpha: 0.9)),
                gradient: const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Colors.white, Color(0xFFF0F6FF)],
                ),
              ),
              child: Stack(
                children: [
                  if (current != null)
                    Positioned(
                      right: -20,
                      bottom: 4,
                      child: Opacity(
                        opacity: 0.24,
                        child: Image.asset(
                          'assets/images/home/home-schedule-diorama.png',
                          width: 136,
                          height: 136,
                        ),
                      ),
                    ),
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 10,
                    ),
                    child: current == null ? _empty() : _schedule(current),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _schedule(NextScheduleModel course) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          children: [
            _status(course.isOngoing ? '正在上课' : '下一节课'),
            const Spacer(),
            const Icon(
              Icons.arrow_forward_rounded,
              color: AppColors.brand,
              size: 19,
            ),
          ],
        ),
        const SizedBox(height: 6),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    course.courseName,
                    style: const TextStyle(
                      color: AppColors.textPrimary,
                      fontSize: 21,
                      fontWeight: FontWeight.w700,
                      height: 1.4,
                      letterSpacing: -0.3,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    '${course.timeRange?.isNotEmpty == true ? course.timeRange! : course.sectionText}  ·  ${course.location?.isNotEmpty == true ? course.location! : '地点待公布'}',
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 13,
                      height: 1.45,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 12),
            Container(
              constraints: const BoxConstraints(minWidth: 64),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0xFF2563EB), Color(0xFF4C9AF7)],
                ),
                borderRadius: BorderRadius.circular(18),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x332563EB),
                    blurRadius: 14,
                    offset: Offset(0, 6),
                  ),
                ],
              ),
              child: Column(
                children: [
                  Text(
                    '${course.startSection}–${course.endSection}',
                    textAlign: TextAlign.center,
                    textScaler: TextScaler.noScaling,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 27,
                      fontWeight: FontWeight.w800,
                      height: 1.2,
                    ),
                  ),
                  const Text(
                    '节次',
                    style: TextStyle(
                      color: Color(0xFFD8E8FF),
                      fontSize: 12,
                      height: 1.5,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _empty() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          children: [
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '我的校园日程',
                    style: TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 13,
                    ),
                  ),
                  SizedBox(height: 14),
                  Text(
                    '给今天留一点从容',
                    style: TextStyle(
                      color: AppColors.textPrimary,
                      fontSize: 21,
                      fontWeight: FontWeight.w700,
                      height: 1.4,
                    ),
                  ),
                ],
              ),
            ),
            Image.asset(
              'assets/images/home/home-empty-cloud-companion.png',
              width: 92,
              height: 92,
            ),
          ],
        ),
        const SizedBox(height: 8),
        const Text(
          '今天没有课程安排，也可以查看完整课表。',
          style: TextStyle(
            color: AppColors.textSecondary,
            fontSize: 14,
            height: 1.5,
          ),
        ),
        const SizedBox(height: 18),
        const Row(
          children: [
            Text(
              '查看课表',
              style: TextStyle(
                color: AppColors.brand,
                fontSize: 14,
                fontWeight: FontWeight.w600,
              ),
            ),
            Spacer(),
            Icon(Icons.arrow_forward_rounded, color: AppColors.brand, size: 20),
          ],
        ),
      ],
    );
  }

  Widget _status(String text) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: AppColors.brandSoft,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: const BoxDecoration(
              color: AppColors.brand,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 6),
          Text(
            text,
            style: const TextStyle(
              color: AppColors.brandDeep,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }

  Widget _detail(IconData icon, String text) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(top: 2),
          child: Icon(icon, size: 16, color: AppColors.textSecondary),
        ),
        const SizedBox(width: 7),
        Expanded(
          child: Text(
            text,
            style: const TextStyle(
              color: AppColors.textSecondary,
              fontSize: 13,
              height: 1.45,
            ),
          ),
        ),
      ],
    );
  }
}
