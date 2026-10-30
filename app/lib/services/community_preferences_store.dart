import 'dart:async';

import 'package:shared_preferences/shared_preferences.dart';

/// 仅保存在当前设备，按账号隔离；写入成功后才更新可见状态。
class CommunityPreferencesStore {
  final String accountId;
  final Future<SharedPreferences> Function() _loadPreferences;
  SharedPreferences? _preferences;
  Future<bool>? _initializing;
  Set<String> _bookmarks = {};
  Set<String> _hidden = {};
  Future<void> _writes = Future<void>.value();

  CommunityPreferencesStore({
    required this.accountId,
    Future<SharedPreferences> Function()? loadPreferences,
  }) : _loadPreferences = loadPreferences ?? SharedPreferences.getInstance;

  String _key(String kind) =>
      'community_local_${Uri.encodeComponent(accountId)}_$kind';

  bool isBookmarked(String postId) => _bookmarks.contains(postId);
  bool isHidden(String postId) => _hidden.contains(postId);

  Future<bool> initialize() {
    if (_preferences != null) return Future.value(true);
    return _initializing ??= _initialize();
  }

  Future<bool> _initialize() async {
    try {
      final preferences = await _loadPreferences();
      _bookmarks = (preferences.getStringList(_key('bookmarks')) ?? []).toSet();
      _hidden = (preferences.getStringList(_key('hidden')) ?? []).toSet();
      _preferences = preferences;
      return true;
    } catch (_) {
      _initializing = null;
      return false;
    }
  }

  Future<bool> setBookmarked(String postId, bool value) =>
      _write(postId, value, bookmark: true);

  Future<bool> setHidden(String postId, bool value) =>
      _write(postId, value, bookmark: false);

  Future<bool> _write(String postId, bool value, {required bool bookmark}) {
    final result = Completer<bool>();
    // 不同帖子的连续操作也串行合并，避免后一次写入覆盖前一次收藏。
    _writes = _writes.then((_) async {
      try {
        if (!await initialize()) {
          result.complete(false);
          return;
        }
        final previous = Set<String>.of(bookmark ? _bookmarks : _hidden);
        final next = Set<String>.of(previous);
        value ? next.add(postId) : next.remove(postId);
        final key = _key(bookmark ? 'bookmarks' : 'hidden');
        final saved = await _save(key, next);
        if (saved) {
          if (bookmark) {
            _bookmarks = next;
          } else {
            _hidden = next;
          }
        } else {
          // SharedPreferences 会先改内存缓存再调用平台写入；失败时也恢复该缓存。
          await _save(key, previous);
        }
        result.complete(saved);
      } catch (_) {
        result.complete(false);
      }
    });
    return result.future;
  }

  Future<bool> _save(String key, Set<String> values) async {
    try {
      return await _preferences!.setStringList(key, values.toList()..sort());
    } catch (_) {
      return false;
    }
  }

  // ==================== P1 服务端化：服务端权威合并支持 ====================

  Set<String> allBookmarked() => Set<String>.of(_bookmarks);
  Set<String> allHidden() => Set<String>.of(_hidden);

  /// 服务端合并后整体替换本地收藏缓存（服务端为准）。
  Future<bool> replaceAllBookmarks(List<String> postIds) =>
      _replaceAll(postIds, bookmark: true);

  /// 服务端合并后整体替换本地隐藏缓存（服务端为准）。
  Future<bool> replaceAllHidden(List<String> postIds) =>
      _replaceAll(postIds, bookmark: false);

  Future<bool> _replaceAll(List<String> postIds, {required bool bookmark}) {
    final result = Completer<bool>();
    _writes = _writes.then((_) async {
      try {
        if (!await initialize()) {
          result.complete(false);
          return;
        }
        final next = postIds.where((id) => id.isNotEmpty).toSet();
        final key = _key(bookmark ? 'bookmarks' : 'hidden');
        final saved = await _save(key, next);
        if (saved) {
          if (bookmark) {
            _bookmarks = next;
          } else {
            _hidden = next;
          }
        }
        result.complete(saved);
      } catch (_) {
        result.complete(false);
      }
    });
    return result.future;
  }
}
