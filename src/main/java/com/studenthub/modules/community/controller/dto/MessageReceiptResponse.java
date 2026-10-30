package com.studenthub.modules.community.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MessageReceiptResponse(int updatedCount, List<Long> messageIds, LocalDateTime at) {
}
