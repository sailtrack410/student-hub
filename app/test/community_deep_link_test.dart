import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/community_api.dart';
import 'package:smartcampus_app/models/community_models.dart';
import 'package:smartcampus_app/services/community_deep_link_service.dart';

import 'community_interaction_test.dart' as fixtures;

/// D-Share 深链验收：
/// - URI 解析：App Links / scheme / 旧数字 id 三形态，非帖子链接拒绝；
/// - 分享链接构建：优先 slug，无 slug 回退数字 id。
void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('URI 解析三种帖子形态', () {
    expect(
      CommunityDeepLinkService.postIdFromUri(
        Uri.parse('https://sailtrack.cn/c/HTOPUVso'),
      ),
      'HTOPUVso',
    );
    expect(
      CommunityDeepLinkService.postIdFromUri(
        Uri.parse('https://sailtrack.cn/c/12345'),
      ),
      '12345',
    );
    expect(
      CommunityDeepLinkService.postIdFromUri(Uri.parse('studenthub://p/HTOPUVso')),
      'HTOPUVso',
    );
    // 非帖子链接拒绝
    expect(
      CommunityDeepLinkService.postIdFromUri(Uri.parse('https://sailtrack.cn/')),
      isNull,
    );
    expect(
      CommunityDeepLinkService.postIdFromUri(
        Uri.parse('https://sailtrack.cn/x/HTOPUVso'),
      ),
      isNull,
    );
    expect(
      CommunityDeepLinkService.postIdFromUri(
        Uri.parse('https://evil.example.com/c/HTOPUVso'),
      ),
      isNull,
    );
    expect(
      CommunityDeepLinkService.postIdFromUri(Uri.parse('studenthub://q/abc')),
      isNull,
    );
  });

  test('分享链接构建：优先 slug，无 slug 回退数字 id', () {
    final base = fixtures.post('9');
    expect(
      CommunityApi.shareLinkOf(base),
      'https://sailtrack.cn/c/9',
      reason: '无 slug 回退数字 id',
    );
    final withSlug = CommunityPost(
      id: '9',
      authorId: 'a',
      authorName: 'x',
      authorLevel: 'Lv.1',
      timeAgo: '刚刚',
      location: '校园',
      title: 't',
      content: 'c',
      shareSlug: 'HTOPUVso',
    );
    expect(
      CommunityApi.shareLinkOf(withSlug),
      'https://sailtrack.cn/c/HTOPUVso',
      reason: '有 slug 用 slug',
    );
  });
}
