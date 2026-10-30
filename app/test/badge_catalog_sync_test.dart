import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/services/badge_catalog_sync.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('BadgeCatalogSync cache', () {
    test('cachedCatalog parses stored catalog json', () async {
      SharedPreferences.setMockInitialValues({
        BadgeCatalogSync.catalogJsonKey: jsonEncode({
          'catalogVersion': '8f3c1d92a7b04e65',
          'changed': true,
          'categories': [
            {'categoryCode': 'COMMUNITY', 'categoryName': '社区互动', 'sortOrder': 10},
          ],
          'badges': [
            {'badgeCode': 'COMMUNITY_FIRST_POST', 'badgeName': '破冰首帖'},
          ],
        }),
        BadgeCatalogSync.catalogVersionKey: '8f3c1d92a7b04e65',
      });

      final catalog = await BadgeCatalogSync.cachedCatalog();
      expect(catalog, isNotNull);
      expect(catalog!['catalogVersion'], '8f3c1d92a7b04e65');
      expect((catalog['badges'] as List), hasLength(1));
      expect(await BadgeCatalogSync.cachedCatalogVersion(), '8f3c1d92a7b04e65');
    });

    test('cachedState parses stored state json', () async {
      SharedPreferences.setMockInitialValues({
        BadgeCatalogSync.stateJsonKey: jsonEncode({
          'catalogVersion': '8f3c1d92a7b04e65',
          'wornBadgeCode': 'LEVEL_3',
          'activated': [
            {'badgeCode': 'COMMUNITY_FIRST_POST', 'unlockedAt': '2026-10-15T14:32:00'},
          ],
          'progress': [
            {'badgeCode': 'COMMUNITY_POST_10', 'currentProgress': 4},
          ],
        }),
      });

      final state = await BadgeCatalogSync.cachedState();
      expect(state, isNotNull);
      expect(state!['wornBadgeCode'], 'LEVEL_3');
      expect((state['activated'] as List), hasLength(1));
      expect((state['progress'] as List), hasLength(1));
    });

    test('returns null for missing or corrupt cache', () async {
      SharedPreferences.setMockInitialValues({
        BadgeCatalogSync.catalogJsonKey: 'not-json',
      });

      expect(await BadgeCatalogSync.cachedCatalog(), isNull);
      expect(await BadgeCatalogSync.cachedState(), isNull);
      expect(await BadgeCatalogSync.cachedCatalogVersion(), isNull);
    });
  });
}
