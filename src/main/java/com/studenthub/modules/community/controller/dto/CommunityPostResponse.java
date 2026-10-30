package com.studenthub.modules.community.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子响应（2026-10-17 批量组装定稿）：authorLevel/authorBadgeCode 随 sys_user 行搭车
 * （批量组装 selectBatchIds 整行取出，零额外查询）；等级 f(exp) 现算，badgeCode 为佩戴列。
 * 2026-10-18 增加 postType 与 contentBlocks（文章图文混排，图片块带签名地址）。
 */
public record CommunityPostResponse(Long postId, Long sectionId, String sectionName, String sectionIcon,
                                    Long authorId, String authorName, String avatarUrl,
                                    Integer authorLevel, String authorBadgeCode,
                                    boolean authorOfficial, String authorOfficialTitle,
                                    String postType, String status, String hiddenReason, String title,
                                    String content, List<String> imageUrls, List<String> thumbnailUrls,
                                    List<CommunityContentBlockResponse> contentBlocks, List<String> topics,
                                    int commentCount, int likeCount, boolean liked, LocalDateTime createdAt,
                                    String locationName, String locationAddress,
                                    java.math.BigDecimal latitude, java.math.BigDecimal longitude, String poiId) {
}
