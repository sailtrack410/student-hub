/// 当前用户登录会话模型
class UserSession {
  /// 当前登录用户 ID（用于判断帖子作者本人等场景）
  static String? currentUserId;
  static String? currentUserName;
  static String? currentCampusId;

  final String token;
  final String userId;
  final String? campusId;
  final String username;
  final String displayName;
  final String? avatarPath;
  final String identityType;
  final String authorizationStatus;
  final bool isNewUser;

  const UserSession({
    required this.token,
    required this.userId,
    this.campusId,
    required this.username,
    required this.displayName,
    this.avatarPath,
    required this.identityType,
    required this.authorizationStatus,
    required this.isNewUser,
  });

  factory UserSession.fromJson(Map<String, dynamic> json, String token) {
    final userMap = json['user'] is Map<String, dynamic>
        ? json['user'] as Map<String, dynamic>
        : json;
    final parsedToken = token.isNotEmpty
        ? token
        : (json['accessToken']?.toString() ?? json['token']?.toString() ?? '');
    return UserSession(
      token: parsedToken,
      userId: userMap['userId']?.toString() ?? '',
      campusId: userMap['campusId']?.toString(),
      username: userMap['username']?.toString() ?? '',
      displayName: userMap['displayName']?.toString() ?? '',
      avatarPath: userMap['avatarPath']?.toString(),
      identityType: userMap['identityType']?.toString() ?? 'STUDENT',
      authorizationStatus:
          userMap['authorizationStatus']?.toString() ?? 'BASIC',
      isNewUser: json['isNewUser'] == true,
    );
  }

  /// 解析资料与切换当前身份分开，避免过期请求恢复上一个账号。
  void activate() {
    currentUserId = userId;
    currentUserName = displayName;
    currentCampusId = campusId;
  }

  static void clearCurrentUser() {
    currentUserId = null;
    currentUserName = null;
    currentCampusId = null;
  }

  Map<String, dynamic> toProfileJson() => {
    'userId': userId,
    'campusId': campusId,
    'username': username,
    'displayName': displayName,
    'avatarPath': avatarPath,
    'identityType': identityType,
    'authorizationStatus': authorizationStatus,
    'isNewUser': isNewUser,
  };

  Map<String, dynamic> toJson() {
    return {
      'token': token,
      'userId': userId,
      'campusId': campusId,
      'username': username,
      'displayName': displayName,
      'avatarPath': avatarPath,
      'identityType': identityType,
      'authorizationStatus': authorizationStatus,
      'isNewUser': isNewUser,
    };
  }
}
