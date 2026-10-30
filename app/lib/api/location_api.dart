import 'api_client.dart';

class PostLocation {
  final String name;
  final String address;
  final double latitude;
  final double longitude;
  final double distance;
  final String poiId;

  const PostLocation({
    required this.name,
    required this.address,
    required this.latitude,
    required this.longitude,
    this.distance = 0,
    this.poiId = '',
  });

  factory PostLocation.fromJson(Map<String, dynamic> json) {
    return PostLocation(
      name: json['name']?.toString() ?? '',
      address: json['address']?.toString() ?? '',
      latitude: double.tryParse('${json['latitude']}') ?? 0,
      longitude: double.tryParse('${json['longitude']}') ?? 0,
      distance: double.tryParse('${json['distance']}') ?? 0,
      poiId: json['id']?.toString() ?? '',
    );
  }
}

class LocationApi {
  static final ApiClient _client = ApiClient();

  static Future<List<PostLocation>> nearby({
    required double latitude,
    required double longitude,
    String? keyword,
    int radius = 1000,
  }) async {
    try {
      final query = <String, String>{
        'latitude': '$latitude',
        'longitude': '$longitude',
        'radius': '$radius',
        if (keyword != null && keyword.trim().isNotEmpty)
          'keyword': keyword.trim(),
      };
      final uri = Uri(
        path: '/api/app/locations/nearby',
        queryParameters: query,
      ).toString();
      final res = await _client.get(uri);
      final list = res is List
          ? res
          : (res is Map && res['data'] is List
                ? res['data'] as List
                : const []);
      return list
          .whereType<Map>()
          .map(
            (e) => PostLocation.fromJson(
              e.map((k, v) => MapEntry(k.toString(), v)),
            ),
          )
          .where((e) => e.name.isNotEmpty)
          .toList();
    } catch (_) {
      return const [];
    }
  }

  static Future<PostLocation?> reverse({
    required double latitude,
    required double longitude,
  }) async {
    try {
      final res = await _client.get(
        '/api/app/locations/reverse?latitude=$latitude&longitude=$longitude',
      );
      if (res is Map<String, dynamic>) {
        final name = res['name']?.toString() ?? '';
        if (name.isEmpty) return null;
        return PostLocation(
          name: name,
          address: res['address']?.toString() ?? name,
          latitude: latitude,
          longitude: longitude,
        );
      }
    } catch (_) {}
    return null;
  }
}
