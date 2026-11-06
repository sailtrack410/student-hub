package com.studenthub.modules.servicerequest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.adminscope.security.AdminDataScope;
import com.studenthub.modules.adminscope.security.AdminDataScopeService;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.mapper.OrganizationMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeavePageQuery;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestLogMapper;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.servicerequest.service.RequestBusinessView;
import com.studenthub.modules.servicerequest.service.RequestLogRecorder;
import com.studenthub.modules.servicerequest.service.impl.LeaveRequestServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaveRequestLegacyStatusTest {

    @Test
    void legacyCompletionWithoutCancellationTimestampIsOnlyReportedAsCompleted() {
        ServiceRequestEntity request = leave("COMPLETED");
        ServiceRequestMapper requests = mock(ServiceRequestMapper.class);
        ServiceRequestLogMapper logs = mock(ServiceRequestLogMapper.class);
        AdminCampusContextService campus = mock(AdminCampusContextService.class);
        AdminDataScopeService scope = mock(AdminDataScopeService.class);
        when(requests.selectById(request.getId())).thenReturn(request);
        when(logs.selectList(any())).thenReturn(List.of());
        when(campus.currentCampusId()).thenReturn(1L);
        when(scope.current()).thenReturn(new AdminDataScope(true, false, false, 1L, List.of()));
        LeaveRequestServiceImpl service = new LeaveRequestServiceImpl(requests, logs,
                mock(ServiceItemMapper.class), mock(UserMapper.class), mock(OrganizationMapper.class),
                mock(RequestLogRecorder.class), mock(NotificationService.class), mock(FileStorageService.class),
                new ObjectMapper(), campus, scope);

        var detail = service.detail(request.getId());

        assertEquals("COMPLETED", detail.status());
        assertEquals("COMPLETED", detail.leaveStatus());
        assertEquals("COMPLETED", detail.businessStatus());
        assertEquals("已办结", detail.statusLabel());
        assertTrue(detail.availableActions().isEmpty());
        assertEquals("COMPLETED", RequestBusinessView.status(request, null));
        assertEquals("已办结", RequestBusinessView.label(request, null));
        assertTrue(RequestBusinessView.actions(request, null, false).isEmpty());
    }

    @Test
    void onlyAnActualCancellationTimestampReportsClosedLeave() {
        ServiceRequestEntity request = leave("COMPLETED");
        request.setLeaveCancelAt(LocalDateTime.of(2026, 11, 7, 10, 0));

        assertEquals("CLOSED", RequestBusinessView.status(request, null));
        assertEquals("已销假", RequestBusinessView.label(request, null));
        assertTrue(RequestBusinessView.actions(request, null, false).isEmpty());
    }

    @Test
    void approvedLeaveRemainsOpenWithoutStudentWithdrawalOrRepairActions() {
        ServiceRequestEntity request = leave("PROCESSING");

        assertEquals("APPROVED", RequestBusinessView.status(request, "PROCESSING"));
        assertEquals("已批准，待销假", RequestBusinessView.label(request, "PROCESSING"));
        assertTrue(RequestBusinessView.actions(request, "PROCESSING", false).isEmpty());
    }

    @Test
    void adminSummaryDistinguishesWithdrawalFromClosureAndExposesManagerActions() {
        ServiceRequestEntity pending = leave("SUBMITTED");
        ServiceRequestEntity withdrawn = leave("CANCELLED");
        ServiceRequestEntity closed = leave("COMPLETED");
        closed.setLeaveCancelAt(LocalDateTime.of(2026, 11, 7, 10, 0));
        ServiceRequestEntity approved = leave("PROCESSING");
        ServiceRequestEntity legacyCompleted = leave("COMPLETED");
        ServiceRequestMapper requests = mock(ServiceRequestMapper.class);
        AdminCampusContextService campus = mock(AdminCampusContextService.class);
        AdminDataScopeService scope = mock(AdminDataScopeService.class);
        Page<ServiceRequestEntity> page = new Page<>(1, 10, 5);
        page.setRecords(List.of(pending, withdrawn, closed, approved, legacyCompleted));
        when(requests.selectPage(any(), any())).thenReturn(page);
        when(campus.currentCampusId()).thenReturn(1L);
        when(scope.current()).thenReturn(new AdminDataScope(true, false, false, 1L, List.of()));
        LeaveRequestServiceImpl service = new LeaveRequestServiceImpl(requests,
                mock(ServiceRequestLogMapper.class), mock(ServiceItemMapper.class), mock(UserMapper.class),
                mock(OrganizationMapper.class), mock(RequestLogRecorder.class), mock(NotificationService.class),
                mock(FileStorageService.class), new ObjectMapper(), campus, scope);

        var records = service.page(new AdminLeavePageQuery(1, 10, null, null, null, null, null)).getRecords();

        assertEquals("LEAVE_APPROVAL", records.get(0).flowType());
        assertEquals("AFFAIR-LEAVE", records.get(0).itemCode());
        assertEquals("PENDING_APPROVAL", records.get(0).businessStatus());
        assertEquals("待审批", records.get(0).statusLabel());
        assertEquals(List.of("APPROVE", "REJECT"), records.get(0).availableActions());
        assertEquals("CANCELLED", records.get(1).leaveStatus());
        assertEquals("CANCELLED", records.get(1).businessStatus());
        assertEquals("已撤回", records.get(1).statusLabel());
        assertTrue(records.get(1).availableActions().isEmpty());
        assertEquals("CANCELLED", records.get(2).leaveStatus());
        assertEquals("CLOSED", records.get(2).businessStatus());
        assertEquals("已销假", records.get(2).statusLabel());
        assertTrue(records.get(2).availableActions().isEmpty());
        assertEquals("APPROVED", records.get(3).businessStatus());
        assertEquals(List.of("CANCEL"), records.get(3).availableActions());
        assertEquals("COMPLETED", records.get(4).businessStatus());
        assertEquals("已办结", records.get(4).statusLabel());
        assertTrue(records.get(4).availableActions().isEmpty());
    }

    private ServiceRequestEntity leave(String status) {
        ServiceRequestEntity request = new ServiceRequestEntity();
        request.setId(19L);
        request.setCampusId(1L);
        request.setFlowType("LEAVE_APPROVAL");
        request.setItemCode("AFFAIR-LEAVE");
        request.setStatus(status);
        return request;
    }
}
