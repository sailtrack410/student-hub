import 'package:flutter/material.dart';


class HomeCampusHeader extends StatelessWidget {
  final String greeting;
  final String date;
  final int unreadCount;
  final VoidCallback onSearch;
  final VoidCallback onNotifications;
  final VoidCallback onAi;

  const HomeCampusHeader({
    super.key,
    required this.greeting,
    required this.date,
    required this.unreadCount,
    required this.onSearch,
    required this.onNotifications,
    required this.onAi,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFF398DE9),
        borderRadius: BorderRadius.vertical(bottom: Radius.circular(32)),
        image: DecorationImage(
          image: AssetImage(
            'assets/images/home/home-sky-campus-background.jpg',
          ),
          fit: BoxFit.cover,
          alignment: Alignment.bottomCenter,
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        children: [
          const Positioned.fill(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Color(0xD1103D9C),
                    Color(0x6B2367C5),
                    Color(0x1483C9F4),
                  ],
                  stops: [0, 0.65, 1],
                ),
              ),
            ),
          ),
          SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.18),
                          borderRadius: BorderRadius.circular(13),
                          border: Border.all(
                            color: Colors.white.withValues(alpha: 0.3),
                          ),
                        ),
                        child: const Icon(
                          Icons.school_rounded,
                          color: Colors.white,
                          size: 22,
                        ),
                      ),
                      const SizedBox(width: 10),
                      const Expanded(
                        child: Text(
                          'STUDENT HUB',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 13,
                            fontWeight: FontWeight.w800,
                            letterSpacing: 1.4,
                          ),
                        ),
                      ),
                      _action(Icons.search_rounded, '全局搜索', onSearch),
                      const SizedBox(width: 8),
                      _action(
                        Icons.notifications_none_rounded,
                        '通知中心',
                        onNotifications,
                        unread: unreadCount,
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Padding(
                    padding: const EdgeInsets.only(right: 92),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          greeting,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 26,
                            fontWeight: FontWeight.w700,
                            height: 1.35,
                            letterSpacing: -0.4,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          date,
                          style: const TextStyle(
                            color: Color(0xFFD7E9FF),
                            fontSize: 13,
                            height: 1.5,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          Positioned(
            right: 4,
            bottom: 12,
            child: Semantics(
              button: true,
              label: '向绵宝AI提问',
              child: InkWell(
                key: const ValueKey('home-header-ai'),
                onTap: onAi,
                borderRadius: BorderRadius.circular(48),
                child: Padding(
                  padding: const EdgeInsets.all(4),
                  child: Image.asset(
                    'assets/images/home/campus-ai-companion.png',
                    width: 88,
                    height: 88,
                    fit: BoxFit.contain,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _action(
    IconData icon,
    String tooltip,
    VoidCallback onTap, {
    int unread = 0,
  }) {
    return IconButton(
      onPressed: onTap,
      tooltip: tooltip,
      style: IconButton.styleFrom(
        backgroundColor: Colors.white.withValues(alpha: 0.15),
      ),
      icon: Badge(
        isLabelVisible: unread > 0,
        label: Text(unread > 99 ? '99+' : '$unread'),
        child: Icon(icon, color: Colors.white, size: 21),
      ),
    );
  }
}
