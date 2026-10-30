import 'package:http/http.dart' as http;

Future<http.StreamedResponse> sendSseRequest(http.BaseRequest request) {
  throw UnsupportedError('SSE streaming is not supported on this platform');
}
