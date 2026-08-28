package com.studenthub.modules.evaluation;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.evaluation.controller.dto.CreateEvaluationRequest;
import com.studenthub.modules.evaluation.controller.dto.EvaluationResponse;
import com.studenthub.modules.evaluation.entity.ServiceEvaluationEntity;
import com.studenthub.modules.evaluation.mapper.ServiceEvaluationMapper;
import com.studenthub.modules.evaluation.service.impl.EvaluationServiceImpl;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.RoleScope;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.servicerequest.service.RequestLogRecorder;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceImplTest {

    @Mock
    private ServiceEvaluationMapper evaluationMapper;
    @Mock
    private ServiceRequestMapper serviceRequestMapper;
    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private RequestLogRecorder requestLogRecorder;
    @Mock
    private NotificationService notificationService;

    private EvaluationServiceImpl evaluationService;

    private AuthenticatedUser requester;
    private ServiceRequestEntity completedRequest;
    private WorkOrderEntity workOrder;

    @BeforeEach
    void setUp() {
        evaluationService = new EvaluationServiceImpl(evaluationMapper, serviceRequestMapper,
                workOrderMapper, requestLogRecorder, notificationService);
        requester = new AuthenticatedUser(1002L, 1L, 10L, "requester", "开发申请人",
                "STUDENT", "BASIC", List.of(new RoleScope("REQUESTER", 10L, null)), List.of());
        completedRequest = new ServiceRequestEntity();
        completedRequest.setId(3001L);
        completedRequest.setRequesterId(1002L);
        completedRequest.setTitle("宿舍报修");
        completedRequest.setStatus("COMPLETED");
        workOrder = new WorkOrderEntity();
        workOrder.setId(4001L);
        workOrder.setRequestId(3001L);
        workOrder.setTitle("宿舍报修");
        workOrder.setAssigneeId(1003L);
        workOrder.setAssigneeName("开发维修员");
    }

    private CreateEvaluationRequest request() {
        return new CreateEvaluationRequest(5, "响应快,态度好", "维修很及时");
    }

    @Test
    void submitSavesEvaluationAndNotifiesWorker() {
        when(serviceRequestMapper.selectById(3001L)).thenReturn(completedRequest);
        when(evaluationMapper.selectByRequestId(3001L)).thenReturn(null);
        when(workOrderMapper.findByRequestId(3001L)).thenReturn(workOrder);

        EvaluationResponse response = evaluationService.submit(requester, 3001L, request());

        assertEquals(5, response.rating());
        ArgumentCaptor<ServiceEvaluationEntity> captor = ArgumentCaptor.forClass(ServiceEvaluationEntity.class);
        verify(evaluationMapper).insert(captor.capture());
        assertEquals(1003L, captor.getValue().getAssigneeId());
        verify(notificationService).send(any());
        verify(requestLogRecorder).record(eq(3001L), eq(4001L), any(), any(), any(),
                eq(1002L), eq("开发申请人"), any());
    }

    @Test
    void nonOwnerGetsNotFound() {
        when(serviceRequestMapper.selectById(3001L)).thenReturn(completedRequest);
        AuthenticatedUser stranger = new AuthenticatedUser(9999L, 1L, 10L, "stranger", "陌生人",
                "STUDENT", "BASIC", List.of(), List.of());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> evaluationService.submit(stranger, 3001L, request()));
        assertEquals(ErrorCode.REQUEST_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void onlyCompletedRequestCanBeEvaluated() {
        completedRequest.setStatus("PROCESSING");
        when(serviceRequestMapper.selectById(3001L)).thenReturn(completedRequest);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> evaluationService.submit(requester, 3001L, request()));
        assertEquals(ErrorCode.REQUEST_STATE_INVALID.getCode(), exception.getCode());
        verify(evaluationMapper, never()).insert(any(ServiceEvaluationEntity.class));
    }

    @Test
    void duplicateEvaluationRejected() {
        when(serviceRequestMapper.selectById(3001L)).thenReturn(completedRequest);
        when(evaluationMapper.selectByRequestId(3001L)).thenReturn(new ServiceEvaluationEntity());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> evaluationService.submit(requester, 3001L, request()));
        assertEquals(ErrorCode.EVALUATION_DUPLICATE.getCode(), exception.getCode());
    }

    @Test
    void evaluationWithoutWorkOrderStillSaves() {
        when(serviceRequestMapper.selectById(3001L)).thenReturn(completedRequest);
        when(evaluationMapper.selectByRequestId(3001L)).thenReturn(null);
        when(workOrderMapper.findByRequestId(3001L)).thenReturn(null);

        EvaluationResponse response = evaluationService.submit(requester, 3001L, request());
        assertEquals(5, response.rating());
        ArgumentCaptor<ServiceEvaluationEntity> captor = ArgumentCaptor.forClass(ServiceEvaluationEntity.class);
        verify(evaluationMapper).insert(captor.capture());
        assertEquals(1002L, captor.getValue().getEvaluatorId());
    }
}
