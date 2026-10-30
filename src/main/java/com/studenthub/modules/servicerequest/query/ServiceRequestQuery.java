package com.studenthub.modules.servicerequest.query;

import java.time.LocalDateTime;
import java.util.List;

public interface ServiceRequestQuery {
    /** 按申请人读取最近申请；最多返回 20 条，过滤规则由申请领域维护。 */
    List<Request> recentForUser(Long userId, StatusFilter status, int limit);

    enum StatusFilter { ALL, IN_PROGRESS, COMPLETED }

    record Request(Long id, String itemName, String title, String status,
                   String requestNo, LocalDateTime submittedAt) {}
}
