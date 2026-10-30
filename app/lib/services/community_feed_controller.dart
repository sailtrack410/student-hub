import 'dart:async';

import 'package:flutter/foundation.dart';

import '../api/ad_slot_api.dart';
import '../api/community_api.dart';
import '../models/community_models.dart';
import 'community_interaction_store.dart';

class CommunityFeedQuery {
  const CommunityFeedQuery({
    required this.category,
    required this.adsEnabled,
    this.sectionId,
    this.keyword = '',
  });
  final String category;
  final int? sectionId;
  final String keyword;
  final bool adsEnabled;
  String get sort => category == '推荐' ? 'HOT' : 'LATEST';
}

class CommunityFeedBatch {
  const CommunityFeedBatch(this.items, this.naturalCount, this.total);
  final List<CommunityFeedItem> items;
  final int naturalCount;
  final int total;
}

abstract interface class CommunityFeedGateway {
  Future<CommunityFeedBatch?> fetch(CommunityFeedQuery query, int pageNum);
}

class ApiCommunityFeedGateway implements CommunityFeedGateway {
  @override
  Future<CommunityFeedBatch?> fetch(
    CommunityFeedQuery query,
    int pageNum,
  ) async {
    if (query.keyword.isNotEmpty || !query.adsEnabled) {
      final page = await CommunityApi.fetchPostPage(
        category: query.category,
        sectionId: query.sectionId,
        keyword: query.keyword,
        sort: query.sort,
        pageNum: pageNum,
        pageSize: 10,
      );
      return page == null
          ? null
          : CommunityFeedBatch(
              page.posts.map(CommunityFeedItem.ofPost).toList(),
              page.posts.length,
              page.total,
            );
    }
    final page = await CommunityApi.fetchFeedPage(
      category: query.category,
      sectionId: query.sectionId,
      sort: query.sort,
      pageNum: pageNum,
      pageSize: 10,
    );
    return page == null
        ? null
        : CommunityFeedBatch(page.items, page.postCount, page.total);
  }
}

/// 分页和请求代数独立于页面；旧分区、旧刷新返回不会污染当前信息流。
class CommunityFeedController extends ChangeNotifier {
  CommunityFeedController({
    required this.interactions,
    CommunityFeedGateway? gateway,
  }) : _gateway = gateway ?? ApiCommunityFeedGateway();
  final CommunityInteractionStore interactions;
  final CommunityFeedGateway _gateway;
  List<CommunityFeedItem> items = [];
  bool loading = true,
      error = false,
      loadingMore = false,
      hasMore = true,
      loadMoreFailed = false;
  int generation = 0, _pageNum = 1, _naturalLoaded = 0;
  bool _disposed = false;
  bool _refreshing = false;

  void seed(List<CommunityPost> posts) {
    items = posts
        .map((p) => CommunityFeedItem.ofPost(interactions.apply(p)))
        .toList();
    loading = false;
    hasMore = false;
  }

  void clear() {
    generation++;
    _refreshing = false;
    items = [];
    loading = true;
    loadingMore = false;
    hasMore = true;
    error = false;
  }

  List<CommunityFeedItem> _visible(Iterable<CommunityFeedItem> source) => source
      .where((i) => i.post == null || !interactions.isHidden(i.post!.id))
      .map(
        (i) => i.post == null
            ? i
            : CommunityFeedItem.ofPost(interactions.apply(i.post!)),
      )
      .toList();
  void applyInteractions() => items = _visible(items);

  Future<void> load(CommunityFeedQuery query, {bool reset = false}) async {
    if (_disposed) return;
    if (reset) {
      _refreshing = true;
      generation++;
      _pageNum = 1;
      _naturalLoaded = 0;
      hasMore = true;
      loadingMore = false;
      if (!query.adsEnabled) items = items.where((i) => !i.isAd).toList();
      loading = items.isEmpty;
    } else {
      if (_refreshing || loading || loadingMore || !hasMore) return;
      loadingMore = true;
    }
    loadMoreFailed = false;
    final requestGeneration = generation;
    final interactionRevision = interactions.revision;
    notifyListeners();
    CommunityFeedBatch? batch;
    try {
      batch = await _gateway.fetch(query, _pageNum);
    } catch (_) {}
    if (_disposed || requestGeneration != generation) return;
    _refreshing = false;
    loading = false;
    loadingMore = false;
    if (batch == null) {
      if (reset) hasMore = false;
      error = items.isEmpty;
      loadMoreFailed = !reset;
      notifyListeners();
      return;
    }
    error = false;
    final received = AdSlotApi.filterFeedItems(
      batch.items,
      adsEnabled: query.adsEnabled,
    );
    interactions.acceptPage(
      received.where((i) => i.post != null).map((i) => i.post!),
      interactionRevision,
    );
    final visible = _visible(received);
    if (reset) {
      items = visible;
    } else {
      final posts = items
          .where((i) => i.post != null)
          .map((i) => i.post!.id)
          .toSet();
      final ads = items
          .where((i) => i.ad != null)
          .map((i) => i.ad!.clickId)
          .toSet();
      items.addAll(
        visible.where(
          (i) => i.post != null
              ? posts.add(i.post!.id)
              : i.ad != null && ads.add(i.ad!.clickId),
        ),
      );
    }
    _naturalLoaded += batch.naturalCount;
    hasMore = batch.naturalCount > 0 && _naturalLoaded < batch.total;
    if (hasMore) _pageNum++;
    notifyListeners();
    unawaited(
      interactions.refreshFollowing(
        received.where((i) => i.post != null).map((i) => i.post!.authorId),
      ),
    );
  }

  @override
  void dispose() {
    _disposed = true;
    generation++;
    super.dispose();
  }
}
