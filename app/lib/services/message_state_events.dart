import 'dart:async';

/// An invalidation signal; counts always come from the authenticated server.
abstract final class MessageStateEvents {
  static final _changes = StreamController<void>.broadcast();
  static Stream<void> get changes => _changes.stream;
  static void changed() => _changes.add(null);
}
