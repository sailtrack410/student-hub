import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smartcampus_app/widgets/app_bottom_navigation.dart';

void main() {
  for (final width in [320.0, 360.0, 390.0, 430.0]) {
    for (final scale in [1.0, 1.3, 1.5]) {
      testWidgets(
        'navigation fits $width dp at $scale text scale and 34dp inset',
        (tester) async {
          tester.view.physicalSize = Size(width, 844);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          int? selected;
          await tester.pumpWidget(
            MaterialApp(
              home: MediaQuery(
                data: MediaQueryData(
                  size: Size(width, 844),
                  padding: const EdgeInsets.only(bottom: 34),
                  textScaler: TextScaler.linear(scale),
                ),
                child: Scaffold(
                  bottomNavigationBar: AppBottomNavigation(
                    currentIndex: 0,
                    unreadCount: 120,
                    onDestinationSelected: (value) => selected = value,
                  ),
                ),
              ),
            ),
          );
          expect(tester.takeException(), isNull);
          final rect = tester.getRect(find.text('首页'));
          expect(rect.bottom, lessThanOrEqualTo(844 - 34));
          expect(
            tester.getSize(find.byType(InkWell).first).height,
            greaterThanOrEqualTo(48),
          );
          await tester.tap(find.text('消息'));
          expect(selected, 3);
          expect(find.text('99+'), findsOneWidget);
        },
      );
    }
  }

  testWidgets('navigation exposes selected destination and unread count', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    try {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            bottomNavigationBar: AppBottomNavigation(
              currentIndex: 3,
              unreadCount: 5,
              onDestinationSelected: (_) {},
            ),
          ),
        ),
      );
      expect(
        tester.getSemantics(find.bySemanticsLabel('消息')),
        matchesSemantics(
          label: '消息',
          value: '5 条未读消息',
          isButton: true,
          isSelected: true,
          hasSelectedState: true,
          hasTapAction: true,
        ),
      );
    } finally {
      handle.dispose();
    }
  });
}
