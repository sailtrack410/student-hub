import 'dart:async';
import 'dart:convert';
import 'dart:html' as html;

import 'package:http/http.dart' as http;

/// Web 端 SSE：用 XMLHttpRequest 的 progress 事件增量读取 responseText。
Future<http.StreamedResponse> sendSseRequest(http.BaseRequest request) async {
  late final StreamController<List<int>> controller;
  final ready = Completer<http.StreamedResponse>();
  final xhr = html.HttpRequest();
  void abort() {
    xhr.abort();
    if (!ready.isCompleted) {
      ready.completeError(http.RequestAbortedException(request.url));
    }
    if (!controller.isClosed) controller.close();
  }

  controller = StreamController<List<int>>(onCancel: abort);
  if (request is http.Abortable) request.abortTrigger?.then((_) => abort());
  xhr
    ..open(request.method, request.url.toString())
    ..responseType = 'text';
  request.headers.forEach(xhr.setRequestHeader);
  xhr.setRequestHeader('Accept', 'text/event-stream');

  var offset = 0;
  void drain() {
    final text = xhr.responseText ?? '';
    if (text.length <= offset) return;
    final chunk = text.substring(offset);
    offset = text.length;
    if (!controller.isClosed) controller.add(utf8.encode(chunk));
  }

  http.StreamedResponse makeResponse(int status) => http.StreamedResponse(
    controller.stream,
    status,
    headers: const {'content-type': 'text/event-stream; charset=utf-8'},
  );

  xhr.onProgress.listen((_) => drain());
  xhr.onReadyStateChange.listen((_) {
    if (xhr.readyState == 2 && !ready.isCompleted) {
      ready.complete(makeResponse(xhr.status ?? 200));
    }
  });
  xhr.onLoad.listen((_) {
    if (!ready.isCompleted) ready.complete(makeResponse(xhr.status ?? 200));
    drain();
    if (!controller.isClosed) controller.close();
  });
  xhr.onError.listen((_) {
    if (!ready.isCompleted) {
      ready.completeError(StateError('SSE XHR error'));
    }
    if (!controller.isClosed) controller.close();
  });

  final body = request is http.Request ? request.bodyBytes : null;
  xhr.send(body);
  return ready.future;
}
