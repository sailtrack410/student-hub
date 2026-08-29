package com.studenthub.modules.community.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SendDirectMessageRequest(@NotNull Long receiverId,
                                       @Size(max = 2000) String content,
                                       List<Long> imageFileIds) {
}
