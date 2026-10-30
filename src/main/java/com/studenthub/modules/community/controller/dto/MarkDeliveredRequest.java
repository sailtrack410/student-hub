package com.studenthub.modules.community.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record MarkDeliveredRequest(@NotEmpty List<Long> messageIds) {
}
