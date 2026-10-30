/// App 客户端版本常量（契约：outputs/app-version-management-contract.md §5）。
///
/// 版本由 Flutter 构建参数从 `pubspec.yaml` 自动注入：
/// - [appVersionCode] 对应 `+` 后的整数，用于服务端比较是否需要更新；
/// - [appVersionName] 对应 `+` 前的展示版本号；
/// - [appChannel] 分发渠道，默认官包 OFFICIAL。
const int appVersionCode = int.fromEnvironment(
  'FLUTTER_BUILD_NUMBER',
  defaultValue: 1,
);
const String appVersionName = String.fromEnvironment(
  'FLUTTER_BUILD_NAME',
  defaultValue: '1.0.0',
);
const String appChannel = 'OFFICIAL';
