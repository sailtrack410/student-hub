package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 发帖请求：postType 决定形态。
 * <ul>
 *   <li>DYNAMIC（缺省）：动态——content 必填（≤2000），title 可选（缺省取正文前 40 字），imageFileIds 为图集；</li>
 *   <li>ARTICLE：文章——title 必填，contentBlocks 图文混排必填（TEXT/IMAGE 顺序即展示顺序，图片 ≤9 张），
 *       正文纯文本摘要写入 content 供列表/搜索使用。</li>
 * </ul>
 */
@Schema(description = "发布社区帖子请求")
public record CreateCommunityPostRequest(
        @Schema(description = "分区 ID") @NotNull Long sectionId,
        @Schema(description = "标题：文章必填（≤80），动态可空") @Size(max = 80) String title,
        @Schema(description = "正文：动态必填（≤2000）；文章可空（由 contentBlocks 承载）") String content,
        @Schema(description = "动态图集（≤9 张，需为自己上传）") @Size(max = 9) List<Long> imageFileIds,
        @Schema(description = "话题（≤8 个）") @Size(max = 8) List<@Size(max = 32) String> topics,
        @Schema(description = "帖子类型：DYNAMIC（缺省）/ ARTICLE") String postType,
        @Schema(description = "文章图文混排块（postType=ARTICLE 必填，≤50 块）")
        @Size(max = 50) List<CommunityContentBlockRequest> contentBlocks,
        @Schema(description = "定位地点名称", nullable = true) @Size(max = 128) String locationName,
        @Schema(description = "定位地址", nullable = true) @Size(max = 255) String locationAddress,
        @Schema(description = "纬度（GCJ-02）", nullable = true) java.math.BigDecimal latitude,
        @Schema(description = "经度（GCJ-02）", nullable = true) java.math.BigDecimal longitude,
        @Schema(description = "高德 POI ID", nullable = true) @Size(max = 64) String poiId) {
}
