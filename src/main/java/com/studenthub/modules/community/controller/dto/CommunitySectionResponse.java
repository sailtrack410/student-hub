package com.studenthub.modules.community.controller.dto;

import com.studenthub.modules.community.entity.CommunitySectionEntity;

public record CommunitySectionResponse(Long sectionId, String sectionCode, String sectionName,
                                       String icon, String description, String bannerTitle,
                                       String bannerSubtitle, String bannerImage) {
    public static CommunitySectionResponse from(CommunitySectionEntity entity) {
        return new CommunitySectionResponse(entity.getId(), entity.getSectionCode(), entity.getSectionName(),
                entity.getIcon(), entity.getDescription(), entity.getBannerTitle(), entity.getBannerSubtitle(),
                entity.getBannerImage());
    }
}
