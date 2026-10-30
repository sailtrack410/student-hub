import 'package:http/http.dart' as http;

Future<http.StreamedResponse> sendSseRequest(http.BaseRequest request) async {
  final client = http.Client();
  try {
    final response = await client.send(request);
    Stream<List<int>> managed() async* {
      try {
        yield* response.stream;
      } finally {
        client.close();
      }
    }

    return http.StreamedResponse(
      managed(),
      response.statusCode,
      headers: response.headers,
      request: response.request,
      reasonPhrase: response.reasonPhrase,
    );
  } catch (_) {
    client.close();
    rethrow;
  }
}
