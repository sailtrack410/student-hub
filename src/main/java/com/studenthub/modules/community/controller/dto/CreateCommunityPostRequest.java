package com.studenthub.modules.community.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateCommunityPostRequest(
        @NotNull Long sectionId,
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 2000) String content,
        @Size(max = 9) List<Long> imageFileIds,
        @Size(max = 8) List<@Size(max = 32) String> topics) {
}
