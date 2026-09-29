/// 当前用户登录会话模型
class UserSession {
  final String token;
  final String userId;
  final String username;
  final String displayName;
  final String identityType;
  final String authorizationStatus;
  final bool isNewUser;

  const UserSession({
    required this.token,
    required this.userId,
    required this.username,
    required this.displayName,
    required this.identityType,
    required this.authorizationStatus,
    required this.isNewUser,
  });

  factory UserSession.fromJson(Map<String, dynamic> json, String token) {
    return UserSession(
      token: token,
      userId: json['userId']?.toString() ?? '',
      username: json['username']?.toString() ?? '',
      displayName: json['displayName']?.toString() ?? '',
      identityType: json['identityType']?.toString() ?? 'STUDENT',
      authorizationStatus: json['authorizationStatus']?.toString() ?? 'BASIC',
      isNewUser: json['isNewUser'] == true,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'token': token,
      'userId': userId,
      'username': username,
      'displayName': displayName,
      'identityType': identityType,
      'authorizationStatus': authorizationStatus,
      'isNewUser': isNewUser,
    };
  }
}
