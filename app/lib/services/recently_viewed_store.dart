import '../models/community_models.dart';

/// 客户端本地浏览历史（服务端暂无浏览历史接口）。
class RecentlyViewedStore {
  static const int _maxItems = 20;
  static final List<CommunityPost> _items = [];

  static List<CommunityPost> get items => List.unmodifiable(_items);
  static int get count => _items.length;

  static void add(CommunityPost post) {
    _items.removeWhere((item) => item.id == post.id);
    _items.insert(0, post);
    if (_items.length > _maxItems) {
      _items.removeRange(_maxItems, _items.length);
    }
  }

  static void clear() => _items.clear();
}
