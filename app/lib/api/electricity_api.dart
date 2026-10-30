import 'api_client.dart';

class ElectricityBill {
  final String sn;
  final String name;
  final String payee;
  final double surplusValue;
  final double totalValue;
  final double unitPrice;
  final int? switchStatus;
  final String readTime;
  final bool payEnable;
  final String protocols;
  final String deviceType;

  const ElectricityBill({
    required this.sn,
    required this.name,
    required this.payee,
    required this.surplusValue,
    required this.totalValue,
    required this.unitPrice,
    this.switchStatus,
    required this.readTime,
    required this.payEnable,
    required this.protocols,
    required this.deviceType,
  });

  factory ElectricityBill.fromJson(Map<String, dynamic> json) {
    return ElectricityBill(
      sn: json['sn']?.toString() ?? '',
      name: json['name']?.toString() ?? '',
      payee: json['payee']?.toString() ?? '',
      surplusValue: double.tryParse('${json['surplusValue']}') ?? 0,
      totalValue: double.tryParse('${json['totalValue']}') ?? 0,
      unitPrice: double.tryParse('${json['unitPrice']}') ?? 0,
      switchStatus: int.tryParse('${json['switchStatus']}'),
      readTime: json['readTime']?.toString() ?? '',
      payEnable: json['payEnable'] == true,
      protocols: json['protocols']?.toString() ?? '',
      deviceType: json['deviceType']?.toString() ?? '',
    );
  }
}

class ElectricityApi {
  static final ApiClient _client = ApiClient();

  static Future<ElectricityBill?> query(String sn) async {
    try {
      final res = await _client.get(
        '/api/app/electricity?sn=${Uri.encodeQueryComponent(sn)}',
      );
      if (res is Map<String, dynamic>) return ElectricityBill.fromJson(res);
    } catch (_) {}
    return null;
  }
}
