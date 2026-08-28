package com.studenthub.modules.servicerequest.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.servicerequest.controller.dto.ServiceRequestDetailResponse;
import com.studenthub.modules.servicerequest.controller.dto.ServiceRequestSummaryResponse;
import com.studenthub.modules.servicerequest.controller.dto.SubmitServiceRequestRequest;

public interface ServiceRequestService {

    ServiceRequestDetailResponse submit(AuthenticatedUser requester, SubmitServiceRequestRequest request,
                                        String idempotencyKey);

    ServiceRequestDetailResponse cancel(AuthenticatedUser requester, Long requestId, String reason);

    PageResult<ServiceRequestSummaryResponse> pageMy(AuthenticatedUser requester, Integer pageNum,
                                                     Integer pageSize, String status);

    ServiceRequestDetailResponse detail(AuthenticatedUser requester, Long requestId);
}
