package com.studenthub.modules.community.controller.dto;

import java.util.List;

public record MarkReadRequest(List<Long> messageIds) {
}
