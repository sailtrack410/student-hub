package com.studenthub.modules.community.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateCommunityCommentRequest(@NotBlank @Size(max = 500) String content,
                                            Long parentCommentId,
                                            List<Long> imageFileIds) {
}
