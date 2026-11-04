import 'package:flutter/material.dart';

import '../api/api_client.dart';

/// 社区动态数据模型定义
/// 严格贴合 Ardot 设计画布 (4:2 社区页) 中各类实体需求，预留后端真实 API 结构
class CommunityPost {
  final String id;
  final String authorId;
  final String authorName;
  final String? authorAvatar;
  final Color avatarBgColor;
  final String authorLevel; // 纯粹等级，如 'Lv.4'，不附加中文称号
  final bool authorOfficial; // 官方账号：展示「官方 · 头衔」替代 Lv
  final String? authorOfficialTitle;
  final Color levelBgColor;
  final Color levelTextColor;
  final String? badgeAsset; // 本地素材路径（如 assets/images/badges/COMMUNITY_HOT_POST.png），null 表示未佩戴
  final String authorDepartment; // 学院专业年级，如 '智能科学与工程学院 · 软件工程 · 2022级'
  final String authorUid; // 学生UID
  final bool isFollowed;
  final String timeAgo;
  final String location; // 宏观校区定位，不写具体楼栋
  final String title;
  final String content;
  final List<String> images;
  final List<String> imageLabels;
  final List<String> topics;
  final String partition;
  final int shareCount;
  final int commentCount;
  final int likeCount;
  final bool isLiked;
  final bool isBookmarked;
  final int viewsCount;
  final String postType;
  final String status;
  final String? hiddenReason;
  final List<CommunityContentBlock> contentBlocks;

  const CommunityPost({
    required this.id,
    required this.authorId,
    required this.authorName,
    this.authorAvatar,
    this.avatarBgColor = const Color(0xFFEFF6FF),
    required this.authorLevel,
    this.authorOfficial = false,
    this.authorOfficialTitle,
    this.levelBgColor = const Color(0xFFEFF6FF),
    this.levelTextColor = const Color(0xFF2563EB),
    this.badgeAsset,
    this.authorDepartment = '智能科学与工程学院 · 软件工程 · 2022级',
    this.authorUid = '8016428',
    this.isFollowed = false,
    required this.timeAgo,
    required this.location,
    required this.title,
    required this.content,
    this.images = const [],
    this.imageLabels = const [],
    this.topics = const [],
    this.partition = '推荐',
    this.shareCount = 0,
    this.commentCount = 0,
    this.likeCount = 0,
    this.isLiked = false,
    this.isBookmarked = false,
    this.viewsCount = 0,
    this.postType = 'DYNAMIC',
    this.status = 'ENABLED',
    this.hiddenReason,
    this.contentBlocks = const [],
  });

  CommunityPost copyWith({
    bool? isFollowed,
    int? likeCount,
    bool? isLiked,
    int? commentCount,
    int? shareCount,
    bool? isBookmarked,
    String? status,
    String? hiddenReason,
  }) {
    return CommunityPost(
      id: id,
      authorId: authorId,
      authorName: authorName,
      authorAvatar: authorAvatar,
      avatarBgColor: avatarBgColor,
      authorLevel: authorLevel,
      authorOfficial: authorOfficial,
      authorOfficialTitle: authorOfficialTitle,
      levelBgColor: levelBgColor,
      levelTextColor: levelTextColor,
      badgeAsset: badgeAsset,
      authorDepartment: authorDepartment,
      authorUid: authorUid,
      isFollowed: isFollowed ?? this.isFollowed,
      timeAgo: timeAgo,
      location: location,
      title: title,
      content: content,
      images: images,
      imageLabels: imageLabels,
      topics: topics,
      partition: partition,
      shareCount: shareCount ?? this.shareCount,
      commentCount: commentCount ?? this.commentCount,
      likeCount: likeCount ?? this.likeCount,
      isLiked: isLiked ?? this.isLiked,
      isBookmarked: isBookmarked ?? this.isBookmarked,
      viewsCount: viewsCount,
      postType: postType,
      status: status ?? this.status,
      hiddenReason: hiddenReason ?? this.hiddenReason,
      contentBlocks: contentBlocks,
    );
  }
}

/// 帖子评论模型
class CommunityComment {
  final String id;
  /// 楼中楼回复的父评论 ID；顶层评论为 null
  final String? parentCommentId;
  final String authorName;
  final Color avatarBgColor;
  final String? avatarPath;
  final String authorLevel; // 纯粹等级，如 'Lv.5'
  final bool authorOfficial;
  final String? authorOfficialTitle;
  final Color levelBgColor;
  final Color levelTextColor;
  final bool isHot;
  final String content;
  final String timeAgo;
  final int likeCount;
  final bool isLiked;
  /// 该评论下的楼中楼回复（服务端 parentCommentId 嵌套），按时间正序
  final List<CommunityComment> replies;

  const CommunityComment({
    required this.id,
    this.parentCommentId,
    required this.authorName,
    this.avatarBgColor = const Color(0xFFF3E8FF),
    this.avatarPath,
    required this.authorLevel,
    this.authorOfficial = false,
    this.authorOfficialTitle,
    this.levelBgColor = const Color(0xFFF3E8FF),
    this.levelTextColor = const Color(0xFF7E22CE),
    this.isHot = false,
    required this.content,
    required this.timeAgo,
    this.likeCount = 0,
    this.isLiked = false,
    this.replies = const [],
  });

  CommunityComment copyWith({
    int? likeCount,
    bool? isLiked,
    List<CommunityComment>? replies,
  }) {
    return CommunityComment(
      id: id,
      parentCommentId: parentCommentId,
      authorName: authorName,
      avatarBgColor: avatarBgColor,
      avatarPath: avatarPath,
      authorLevel: authorLevel,
      authorOfficial: authorOfficial,
      authorOfficialTitle: authorOfficialTitle,
      levelBgColor: levelBgColor,
      levelTextColor: levelTextColor,
      isHot: isHot,
      content: content,
      timeAgo: timeAgo,
      likeCount: likeCount ?? this.likeCount,
      isLiked: isLiked ?? this.isLiked,
      replies: replies ?? this.replies,
    );
  }
}

/// 私聊对话消息模型
class ChatMessage {
  final String id;
  final bool isMe;
  final String text;
  final String time;
  final List<String> imageUrls;
  final List<String> imageNames;
  final List<String> fileIds;
  final bool failed;
  final String status;
  final String? postCardTitle;
  final String? postCardSnippet;
  final String? postCardTag;

  const ChatMessage({
    required this.id,
    required this.isMe,
    required this.text,
    required this.time,
    this.imageUrls = const [],
    this.imageNames = const [],
    this.fileIds = const [],
    this.failed = false,
    this.status = 'SENT',
    this.postCardTitle,
    this.postCardSnippet,
    this.postCardTag,
  });
}

/// 用户勋章成就模型
class UserBadgeItem {
  final String id;
  final String name;
  final String desc;
  final String assetPath;
  final String unlockedDate;
  final bool isEquipped;

  const UserBadgeItem({
    required this.id,
    required this.name,
    required this.desc,
    required this.assetPath,
    required this.unlockedDate,
    this.isEquipped = false,
  });
}

/// 用户他人主页模型
class AuthorProfileInfo {
  final String authorId;
  final String authorName;
  final String? avatarUrl;
  final Color avatarBgColor;
  final String authorLevel; // 'Lv.4'
  final bool authorOfficial;
  final String? authorOfficialTitle;
  final String? badgeAsset; // 佩戴的勋章素材
  final String department;
  final String uid;
  final int postCount;
  final int likeCount;
  final int followCount;
  final int fansCount;
  final bool isFollowed;
  final List<UserBadgeItem> earnedBadges;

  const AuthorProfileInfo({
    required this.authorId,
    required this.authorName,
    this.avatarUrl,
    required this.avatarBgColor,
    required this.authorLevel,
    this.authorOfficial = false,
    this.authorOfficialTitle,
    this.badgeAsset,
    required this.department,
    required this.uid,
    required this.postCount,
    required this.likeCount,
    required this.followCount,
    required this.fansCount,
    required this.isFollowed,
    this.earnedBadges = const [],
  });
}

/// 默认内置全量真实拟真数据（完全对齐设计稿 5:157, 5:316, 5:1464, 5:1778 节点内容）
class CommunityMockData {
  static List<CommunityPost> getInitialPosts() {
    return [
      const CommunityPost(
        id: 'post_101',
        authorId: 'user_201',
        authorName: '追光的少年',
        avatarBgColor: Color(0xFFEFF6FF),
        authorLevel: 'Lv.4',
        levelBgColor: Color(0xFFEFF6FF),
        levelTextColor: Color(0xFF2563EB),
        badgeAsset: 'assets/images/badges/COMMUNITY_HOT_POST.png',
        authorDepartment: '智能科学与工程学院 · 软件工程 · 2022级',
        authorUid: '8016428',
        isFollowed: false,
        timeAgo: '2小时前',
        location: '绵阳城市学院 (游仙校区)',
        title: '关于自习室占座现象的建议与思考，大家怎么看？',
        content:
            '期末复习周座位越来越紧张，很多桌子一大早放了一本书，人却一上午不来。建议自习室上线智能扫码签到、超时释放机制，大家觉得可行吗？',
        imageLabels: ['自习室清晨', '占座书籍', '倡议便利贴'],
        topics: ['校园生活', '期末备战'],
        partition: '学习交流',
        shareCount: 18,
        commentCount: 64,
        likeCount: 286,
        viewsCount: 1280,
      ),
      const CommunityPost(
        id: 'post_102',
        authorId: 'user_202',
        authorName: '薄荷汽水',
        avatarBgColor: Color(0xFFE6FFFA),
        authorLevel: 'Lv.2',
        levelBgColor: Color(0xFFCCFBF1),
        levelTextColor: Color(0xFF0D9488),
        badgeAsset: null, // 未佩戴勋章，不显示任何勋章图标
        authorDepartment: '商学院 · 电子商务 · 2023级',
        authorUid: '8023190',
        isFollowed: true,
        timeAgo: '4小时前',
        location: '绵阳城市学院 (安州校区)',
        title: '三食堂新开的轻食减脂餐亲测推荐！',
        content: '香煎黑椒鸡胸肉套餐只要15块！配菜有西兰花、紫薯和水煮蛋，分量真的很顶，低卡又管饱，减脂期的宝子们冲鸭~',
        imageLabels: ['鸡胸肉轻食实拍', '档口价格单'],
        topics: ['校园美食'],
        partition: '推荐',
        shareCount: 6,
        commentCount: 29,
        likeCount: 142,
        viewsCount: 890,
      ),
      const CommunityPost(
        id: 'post_103',
        authorId: 'user_203',
        authorName: '苏晴晴',
        avatarBgColor: Color(0xFFFDE8F4),
        authorLevel: 'Lv.3',
        levelBgColor: Color(0xFFFEF3C7),
        levelTextColor: Color(0xFFD97706),
        badgeAsset: 'assets/images/badges/JW_FIRST_AUTH.png',
        authorDepartment: '外国语学院 · 英语语言文学 · 2023级',
        authorUid: '8019842',
        isFollowed: false,
        timeAgo: '今天 14:18',
        location: '绵阳城市学院 (游仙校区)',
        title: '在教学楼捡到校园卡，急寻失主！',
        content: '在第二排靠窗捡到一张校园卡，卡面姓名苏**，请失主速与我联系或私聊，看到请帮忙转发扩散！',
        imageLabels: ['校园卡照片'],
        topics: ['失物招领', '校园日常'],
        partition: '失物寻物',
        shareCount: 32,
        commentCount: 12,
        likeCount: 98,
        viewsCount: 620,
      ),
      const CommunityPost(
        id: 'post_104',
        authorId: 'user_204',
        authorName: '考研上岸人',
        avatarBgColor: Color(0xFFFDF2F8),
        authorLevel: 'Lv.5',
        levelBgColor: Color(0xFFF3E8FF),
        levelTextColor: Color(0xFF7E22CE),
        badgeAsset: null, // 未佩戴勋章
        authorDepartment: '计算机与软件学院 · 软件工程 · 2021级',
        authorUid: '7982015',
        isFollowed: false,
        timeAgo: '昨天 19:40',
        location: '绵阳城市学院',
        title: '计算机考研专业课408真题资料低价出',
        content: '上岸学长毕业回血，王道全套四本九成新，笔记很全，附赠近十年统考真题解析手写版，校内自提可刀！',
        imageLabels: ['王道教材实拍', '学长手写笔记'],
        topics: ['二手闲置', '考研经验'],
        partition: '二手闲置',
        shareCount: 5,
        commentCount: 19,
        likeCount: 77,
        viewsCount: 1100,
      ),
    ];
  }

  static List<CommunityComment> getPostComments(String postId) {
    return [
      const CommunityComment(
        id: 'comment_1',
        authorName: '绵城干饭王',
        avatarBgColor: Color(0xFFF3E8FF),
        authorLevel: 'Lv.5',
        levelBgColor: Color(0xFFF3E8FF),
        levelTextColor: Color(0xFF7E22CE),
        isHot: true,
        content: '强烈支持！上周遇到几个位置一占就是一整天，阿姨收书还会被吵，必须靠扫码规范！',
        timeAgo: '1小时前',
        likeCount: 89,
        isLiked: false,
      ),
      const CommunityComment(
        id: 'comment_2',
        authorName: '薄荷汽水',
        avatarBgColor: Color(0xFFCCFBF1),
        authorLevel: 'Lv.2',
        levelBgColor: Color(0xFFCCFBF1),
        levelTextColor: Color(0xFF0D9488),
        isHot: false,
        content: '确实，之前去自习室也是这样，希望能尽快推进！',
        timeAgo: '35分钟前',
        likeCount: 34,
        isLiked: false,
        replies: [
          CommunityComment(
            id: 'comment_2_reply',
            parentCommentId: 'comment_2',
            authorName: '追光的少年(作者)',
            authorLevel: 'Lv.3',
            content: '已经向后勤处反馈了，据说下月开始试点！',
            timeAgo: '20分钟前',
          ),
        ],
      ),
      const CommunityComment(
        id: 'comment_3',
        authorName: '清晨微风',
        avatarBgColor: Color(0xFFEFF6FF),
        authorLevel: 'Lv.3',
        levelBgColor: Color(0xFFEFF6FF),
        levelTextColor: Color(0xFF2563EB),
        isHot: false,
        content: '其实可以像高铁选座一样，自习室系统也支持预约座位和离座倒计时。',
        timeAgo: '15分钟前',
        likeCount: 12,
        isLiked: false,
      ),
    ];
  }

  static List<ChatMessage> getInitialChatMessages() {
    return [
      const ChatMessage(
        id: 'chat_msg_1',
        isMe: false,
        text: '同学你好！我看到你发的帖子，那张卡好像是我上节课落下的！请问还在你那里吗？',
        time: '今天 14:18',
        postCardTitle: '在教学楼捡到校园卡，急寻失主！',
        postCardSnippet: '在第二排靠窗捡到一张校园卡，卡面姓名苏**...',
        postCardTag: '分享帖子 · 校园日常',
      ),
      const ChatMessage(
        id: 'chat_msg_2',
        isMe: true,
        text: '在的！卡背面的姓名是“苏*”，我刚下课，正准备去食堂吃午饭。',
        time: '今天 14:20',
      ),
      const ChatMessage(
        id: 'chat_msg_3',
        isMe: false,
        text: '太感谢了！我就在食堂门口，穿白色卫衣，现在能过去找你拿吗？',
        time: '今天 14:21',
      ),
      const ChatMessage(
        id: 'chat_msg_4',
        isMe: true,
        text: '好，我穿深蓝色夹克，大概5分钟后到食堂门口，稍等！',
        time: '今天 14:22',
      ),
    ];
  }

  static AuthorProfileInfo getAuthorProfile(
    String authorId, {
    String? defaultName,
    Color? defaultAvatarBg,
  }) {
    if (authorId == 'user_203') {
      return const AuthorProfileInfo(
        authorId: 'user_203',
        authorName: '苏晴晴',
        avatarBgColor: Color(0xFFFDE8F4),
        authorLevel: 'Lv.3',
        badgeAsset: 'assets/images/badges/JW_FIRST_AUTH.png',
        department: '外国语学院 · 英语语言文学 · 2023级',
        uid: '8019842',
        postCount: 5,
        likeCount: 188,
        followCount: 26,
        fansCount: 94,
        isFollowed: false,
        earnedBadges: [
          UserBadgeItem(
            id: 'b1',
            name: '教务初认证',
            desc: '完成全日制教务身份首次认证',
            assetPath: 'assets/images/badges/JW_FIRST_AUTH.png',
            unlockedDate: '2023-09-15',
            isEquipped: true,
          ),
          UserBadgeItem(
            id: 'b2',
            name: '首发之星',
            desc: '在社区发表第一条动态',
            assetPath: 'assets/images/badges/COMMUNITY_FIRST_POST.png',
            unlockedDate: '2023-10-01',
          ),
          UserBadgeItem(
            id: 'b3',
            name: '课表达人',
            desc: '首次成功同步并导入教务课表',
            assetPath: 'assets/images/badges/SCHEDULE_FIRST_IMPORT.png',
            unlockedDate: '2023-10-10',
          ),
        ],
      );
    } else if (authorId == 'user_202') {
      return const AuthorProfileInfo(
        authorId: 'user_202',
        authorName: '薄荷汽水',
        avatarBgColor: Color(0xFFCCFBF1),
        authorLevel: 'Lv.2',
        badgeAsset: null, // 未佩戴勋章
        department: '商学院 · 电子商务 · 2023级',
        uid: '8023190',
        postCount: 3,
        likeCount: 156,
        followCount: 18,
        fansCount: 68,
        isFollowed: true,
        earnedBadges: [
          UserBadgeItem(
            id: 'b1',
            name: '首发之星',
            desc: '在社区发表第一篇动态',
            assetPath: 'assets/images/badges/COMMUNITY_FIRST_POST.png',
            unlockedDate: '2023-11-02',
          ),
        ],
      );
    } else if (authorId == 'user_204') {
      return const AuthorProfileInfo(
        authorId: 'user_204',
        authorName: '考研上岸人',
        avatarBgColor: Color(0xFFFDF2F8),
        authorLevel: 'Lv.5',
        badgeAsset: null, // 未佩戴勋章
        department: '计算机与软件学院 · 软件工程 · 2021级',
        uid: '7982015',
        postCount: 14,
        likeCount: 642,
        followCount: 45,
        fansCount: 380,
        isFollowed: false,
        earnedBadges: [
          UserBadgeItem(
            id: 'b1',
            name: '热门达人',
            desc: '单篇动态收获超过50次点赞互动',
            assetPath: 'assets/images/badges/COMMUNITY_LIKES_50.png',
            unlockedDate: '2023-04-12',
          ),
          UserBadgeItem(
            id: 'b2',
            name: '活跃十篇',
            desc: '累计在校园社区发表10篇动态',
            assetPath: 'assets/images/badges/COMMUNITY_POST_10.png',
            unlockedDate: '2023-06-08',
          ),
        ],
      );
    } else {
      // Default: 追光的少年 (user_201)
      return AuthorProfileInfo(
        authorId: authorId,
        authorName: defaultName ?? '追光的少年',
        avatarBgColor: defaultAvatarBg ?? const Color(0xFFEFF6FF),
        authorLevel: 'Lv.4',
        badgeAsset: 'assets/images/badges/COMMUNITY_HOT_POST.png',
        department: '智能科学与工程学院 · 软件工程 · 2022级',
        uid: '8016428',
        postCount: 8,
        likeCount: 235,
        followCount: 32,
        fansCount: 120,
        isFollowed: false,
        earnedBadges: const [
          UserBadgeItem(
            id: 'b1',
            name: '热议达人',
            desc: '所发帖子进入校园热榜',
            assetPath: 'assets/images/badges/COMMUNITY_HOT_POST.png',
            unlockedDate: '2024-03-10',
            isEquipped: true,
          ),
          UserBadgeItem(
            id: 'b2',
            name: '十帖达人',
            desc: '社区累计发帖达到10篇',
            assetPath: 'assets/images/badges/COMMUNITY_POST_10.png',
            unlockedDate: '2024-04-01',
          ),
          UserBadgeItem(
            id: 'b3',
            name: '初次认证',
            desc: '完成学生教务一键学籍认证',
            assetPath: 'assets/images/badges/JW_FIRST_AUTH.png',
            unlockedDate: '2023-09-01',
          ),
        ],
      );
    }
  }
}

/// 社区分区（对应 GET /api/app/community/sections）
class CommunitySection {
  final String id;
  final String code;
  final String name;

  const CommunitySection({
    required this.id,
    required this.code,
    required this.name,
  });
}

/// 社区帖子分页结果
class CommunityPostPage {
  final List<CommunityPost> posts;
  final int total;

  const CommunityPostPage({required this.posts, required this.total});
}

/// 社区等级称号与配色（画布口径：Lv.4 菁英铂金 / Lv.2 进阶白银 / Lv.5 先锋钻石）
class CommunityLevelStyle {
  final String title;
  final Color bgColor;
  final Color textColor;

  const CommunityLevelStyle({
    required this.title,
    required this.bgColor,
    required this.textColor,
  });
}

CommunityLevelStyle communityLevelStyle(String level) {
  final match = RegExp(r'\d+').firstMatch(level);
  final n = int.tryParse(match?.group(0) ?? '') ?? 4;
  switch (n) {
    case 1:
      return const CommunityLevelStyle(
        title: '青铜新手',
        bgColor: Color(0xFFF1F5F9),
        textColor: Color(0xFF64748B),
      );
    case 2:
      return const CommunityLevelStyle(
        title: '进阶白银',
        bgColor: Color(0xFFECFDF5),
        textColor: Color(0xFF059669),
      );
    case 3:
      return const CommunityLevelStyle(
        title: '黄金达人',
        bgColor: Color(0xFFFEF3C7),
        textColor: Color(0xFFD97706),
      );
    case 5:
      return const CommunityLevelStyle(
        title: '先锋钻石',
        bgColor: Color(0xFFF3E8FF),
        textColor: Color(0xFF7E22CE),
      );
    case 6:
    case 7:
    case 8:
    case 9:
      return const CommunityLevelStyle(
        title: '荣耀王者',
        bgColor: Color(0xFFFEF2F2),
        textColor: Color(0xFFDC2626),
      );
    case 4:
    default:
      return const CommunityLevelStyle(
        title: '菁英铂金',
        bgColor: Color(0xFFEFF6FF),
        textColor: Color(0xFF2563EB),
      );
  }
}

/// 佩戴勋章编码 → 本地素材路径
const Set<String> kCommunityBadgeCodes = {
  'COMMUNITY_COMMENT_20',
  'COMMUNITY_FIRST_POST',
  'COMMUNITY_HOT_POST',
  'COMMUNITY_LIKES_50',
  'COMMUNITY_POST_10',
  'COMMUNITY_POST_50',
  'JW_FIRST_AUTH',
  'LEVEL_3',
  'LEVEL_5',
  'LEVEL_7',
  'LEVEL_9',
  'PROFILE_COMPLETE',
  'SCHEDULE_FIRST_IMPORT',
  'SERVICE_COMPLETED_5',
  'SERVICE_EVALUATION_5',
  'SERVICE_FIRST_REQUEST',
  'SERVICE_FIVE_STAR_10',
  'WORKER_FIRST_ORDER',
  'WORKER_ORDER_20',
};

String? communityBadgeAsset(String? code) {
  if (code == null || code.isEmpty || !kCommunityBadgeCodes.contains(code)) {
    return null;
  }
  return 'assets/images/badges/$code.png';
}

/// 帖子详情 + 评论流（GET /api/app/community/posts/{postId}）
class CommunityPostDetail {
  final CommunityPost post;
  final List<CommunityComment> comments;

  const CommunityPostDetail({required this.post, required this.comments});
}

/// 文章图文混排块（postType=ARTICLE）
class CommunityContentBlock {
  final String type; // TEXT / IMAGE
  final String? text;
  final String? fileId;
  final String? url;
  final String? thumbnailUrl;

  const CommunityContentBlock({
    required this.type,
    this.text,
    this.fileId,
    this.url,
    this.thumbnailUrl,
  });
}

/// 私信消息
class DirectMessage {
  final String id;
  final String senderId;
  final String receiverId;
  final String content;
  final List<String> imageUrls;
  final DateTime? createdAt;
  final DateTime? deliveredAt;
  final DateTime? readAt;
  final String status;

  const DirectMessage({
    required this.id,
    required this.senderId,
    required this.receiverId,
    this.content = '',
    this.imageUrls = const [],
    this.createdAt,
    this.deliveredAt,
    this.readAt,
    this.status = 'SENT',
  });

  factory DirectMessage.fromJson(Map<String, dynamic> json) {
    final images = ((json['imageUrls'] as List?) ?? const [])
        .map((e) => e.toString())
        .where((e) => e.isNotEmpty)
        .map((e) => e.startsWith('http') ? e : '${ApiClient.defaultBaseUrl}$e')
        .toList();
    return DirectMessage(
      id: json['messageId']?.toString() ?? json['id']?.toString() ?? '',
      senderId: json['senderId']?.toString() ?? '',
      receiverId: json['receiverId']?.toString() ?? '',
      content: json['content']?.toString() ?? '',
      imageUrls: images,
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? ''),
      deliveredAt: DateTime.tryParse(json['deliveredAt']?.toString() ?? ''),
      readAt: DateTime.tryParse(json['readAt']?.toString() ?? ''),
      status: json['status']?.toString() ?? 'SENT',
    );
  }

  bool isMe(String currentUserId) => senderId == currentUserId;
}

/// 私信会话联系人
class DirectConversation {
  final String peerId;
  final String peerName;
  final String? avatarUrl;
  final String lastContent;
  final DateTime? lastAt;

  const DirectConversation({
    required this.peerId,
    required this.peerName,
    this.avatarUrl,
    this.lastContent = '',
    this.lastAt,
  });
}

/// 帖子详情拉取结果：区分「内容不存在/被隐藏」与网络失败
class PostDetailFetchResult {
  final CommunityPostDetail? detail;
  final bool notFound;

  const PostDetailFetchResult({this.detail, this.notFound = false});
}

/// 用户名片上的勋章预览（GET /api/app/users/{userId}/card）
class UserCardBadge {
  final String badgeCode;
  final String name;
  final String? iconUrl;

  const UserCardBadge({
    required this.badgeCode,
    required this.name,
    this.iconUrl,
  });
}

class UserCardInfo {
  final String userId;
  final String displayName;
  final List<UserCardBadge> previewBadges;

  const UserCardInfo({
    required this.userId,
    required this.displayName,
    this.previewBadges = const [],
  });

  UserCardBadge? get firstBadge =>
      previewBadges.isEmpty ? null : previewBadges.first;
}

/// 官方直投广告卡片（GET /api/app/community/feed 中 itemType=AD）
///
/// clickId 由后端在组装信息流时生成，是「曝光→点击→落地→停留」整条链路的幂等键；
/// landingType 决定点击后的打开方式与是否可测跳出。
class CommunityAd {
  final String adId;
  final String clickId;
  final String title;
  final String brandName;
  final String description;
  final String? coverUrl;
  final String targetUrl;
  final String landingType;
  final String? sectionId;
  final int priority;
  final bool measurable;
  final String sponsoredLabel;
  final String? ctaText;

  const CommunityAd({
    required this.adId,
    required this.clickId,
    required this.title,
    required this.brandName,
    required this.description,
    this.coverUrl,
    required this.targetUrl,
    required this.landingType,
    this.sectionId,
    this.priority = 0,
    required this.measurable,
    this.sponsoredLabel = '推广',
    this.ctaText,
  });

  factory CommunityAd.fromJson(Map<String, dynamic> json) {
    final landingType = json['landingType']?.toString() ?? 'IN_APP_WEBVIEW';
    final rawMeasurable = json['measurable'];
    final measurable = rawMeasurable is bool
        ? rawMeasurable
        : const {'IN_APP_WEBVIEW', 'APP_PAGE'}.contains(landingType);
    final label = json['sponsoredLabel']?.toString() ?? '';
    return CommunityAd(
      adId: json['adId']?.toString() ?? '',
      clickId: json['clickId']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      brandName: json['brandName']?.toString() ?? '',
      description: json['description']?.toString() ?? '',
      coverUrl: _communityMediaUrl(json['coverUrl']),
      targetUrl: json['targetUrl']?.toString() ?? '',
      landingType: landingType,
      sectionId: _communityNullableText(json['sectionId']),
      priority: _communityInt(json['priority']) ?? 0,
      measurable: measurable,
      sponsoredLabel: label.isEmpty ? '推广' : label,
      ctaText: _communityNullableText(json['ctaText']),
    );
  }

  /// 站内 WebView 承接（可测跳出）：IN_APP_WEBVIEW / APP_PAGE
  bool get isWebViewLanding =>
      landingType == 'IN_APP_WEBVIEW' || landingType == 'APP_PAGE';

  /// 系统浏览器承接（不可测跳出）
  bool get isExternalBrowser => landingType == 'EXTERNAL_BROWSER';

  /// Deeplink 承接（不可测跳出）
  bool get isDeeplink => landingType == 'DEEPLINK';

  /// 仅站内 WebView 且有可测标记时，才上报 landing/loaded 与 landing/exit
  bool get isTrackableLanding => isWebViewLanding && measurable;

  String get displayCta =>
      (ctaText != null && ctaText!.isNotEmpty) ? ctaText! : '了解详情';

  CommunityAd copyWith({
    String? clickId,
    String? landingType,
    bool? measurable,
  }) {
    return CommunityAd(
      adId: adId,
      clickId: clickId ?? this.clickId,
      title: title,
      brandName: brandName,
      description: description,
      coverUrl: coverUrl,
      targetUrl: targetUrl,
      landingType: landingType ?? this.landingType,
      sectionId: sectionId,
      priority: priority,
      measurable: measurable ?? this.measurable,
      sponsoredLabel: sponsoredLabel,
      ctaText: ctaText,
    );
  }
}

/// 社区混合信息流条目：自然帖子或官方直投广告
class CommunityFeedItem {
  final String itemType; // POST / AD
  final CommunityPost? post;
  final CommunityAd? ad;

  const CommunityFeedItem({required this.itemType, this.post, this.ad});

  static CommunityFeedItem ofPost(CommunityPost post) =>
      CommunityFeedItem(itemType: 'POST', post: post);

  static CommunityFeedItem ofAd(CommunityAd ad) =>
      CommunityFeedItem(itemType: 'AD', ad: ad);

  bool get isPost => itemType == 'POST' && post != null;
  bool get isAd => itemType == 'AD' && ad != null;

  factory CommunityFeedItem.fromJson(
    Map<String, dynamic> json, {
    required CommunityPost Function(Map<String, dynamic>) postParser,
  }) {
    final type = json['itemType']?.toString() ?? 'POST';
    if (type == 'AD') {
      final ad = json['ad'];
      if (ad is Map<String, dynamic>) {
        return CommunityFeedItem.ofAd(CommunityAd.fromJson(ad));
      }
      return const CommunityFeedItem(itemType: 'AD');
    }
    final post = json['post'];
    if (post is Map<String, dynamic>) {
      return CommunityFeedItem.ofPost(postParser(post));
    }
    return const CommunityFeedItem(itemType: 'POST');
  }
}

/// 混合信息流分页结果（total 为自然内容总数，不是 items.length）
class CommunityFeedPage {
  final List<CommunityFeedItem> items;
  final int total;
  final int pageNum;
  final int pageSize;

  const CommunityFeedPage({
    required this.items,
    required this.total,
    this.pageNum = 1,
    this.pageSize = 10,
  });

  factory CommunityFeedPage.fromJson(
    Map<String, dynamic> json, {
    required CommunityPost Function(Map<String, dynamic>) postParser,
  }) {
    final rawRecords = (json['records'] as List?) ?? const [];
    final items = rawRecords
        .whereType<Map<String, dynamic>>()
        .map((item) => CommunityFeedItem.fromJson(item, postParser: postParser))
        .where((item) => item.post != null || item.ad != null)
        .toList();
    return CommunityFeedPage(
      items: items,
      total:
          _communityInt(json['total']) ?? items.where((i) => i.isPost).length,
      pageNum: _communityInt(json['pageNum']) ?? 1,
      pageSize:
          _communityInt(json['pageSize']) ??
          items.where((i) => i.isPost).length,
    );
  }

  int get postCount => items.where((item) => item.isPost).length;
}

/// 广告点击上报结果（POST /api/app/community/direct-ads/{adId}/click）
class AdClickResult {
  final String clickId;
  final String adId;
  final String targetUrl;
  final String landingType;
  final bool measurable;

  const AdClickResult({
    required this.clickId,
    required this.adId,
    required this.targetUrl,
    required this.landingType,
    required this.measurable,
  });

  factory AdClickResult.fromJson(Map<String, dynamic> json) {
    final landingType = json['landingType']?.toString() ?? '';
    final rawMeasurable = json['measurable'];
    return AdClickResult(
      clickId: json['clickId']?.toString() ?? '',
      adId: json['adId']?.toString() ?? '',
      targetUrl: json['targetUrl']?.toString() ?? '',
      landingType: landingType,
      measurable: rawMeasurable is bool
          ? rawMeasurable
          : const {'IN_APP_WEBVIEW', 'APP_PAGE'}.contains(landingType),
    );
  }
}

int? _communityInt(dynamic value) {
  if (value is int) return value;
  return int.tryParse(value?.toString() ?? '');
}

String? _communityMediaUrl(dynamic value) {
  final text = _communityNullableText(value);
  if (text == null) return null;
  if (text.startsWith('http://') || text.startsWith('https://')) return text;
  return '${ApiClient.defaultBaseUrl}$text';
}

String? _communityNullableText(dynamic value) {
  if (value == null) return null;
  final text = value.toString();
  return text.isEmpty ? null : text;
}
