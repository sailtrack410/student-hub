package com.studenthub.modules.servicerequest.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeaveDetailResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeavePageQuery;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeaveStatsResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeaveSummaryResponse;

/**
 * 独立请假审批：数据来自 service_request 中 item_code = AFFAIR-LEAVE 的申请。
 * 通过 = SUBMITTED → PROCESSING（请假生效）；驳回 = REJECTED；销假 = PROCESSING → COMPLETED 并记录 leave_cancel_at。
 */
public interface LeaveRequestService {

    PageResult<AdminLeaveSummaryResponse> page(AdminLeavePageQuery query);

    AdminLeaveStatsResponse stats();

    AdminLeaveDetailResponse detail(Long requestId);

    void approve(AuthenticatedUser operator, Long requestId, String comment);

    void reject(AuthenticatedUser operator, Long requestId, String reason);

    /** 销假：仅已通过（PROCESSING）且未销假的申请可执行，办结后 status=COMPLETED。 */
    void cancelLeave(AuthenticatedUser operator, Long requestId, String comment);
}
