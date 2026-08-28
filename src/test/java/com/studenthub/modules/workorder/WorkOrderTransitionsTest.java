package com.studenthub.modules.workorder;

import com.studenthub.exception.BusinessException;
import com.studenthub.modules.workorder.statemachine.WorkOrderTransitions;
import com.studenthub.modules.workorder.statemachine.WorkOrderTransitions.Action;
import com.studenthub.modules.workorder.statemachine.WorkOrderTransitions.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkOrderTransitionsTest {

    @Test
    void assignAllowedOnlyBeforeProcessing() {
        assertEquals(Status.ASSIGNED, WorkOrderTransitions.target(Action.ASSIGN, Status.PENDING_DISPATCH));
        assertEquals(Status.ASSIGNED, WorkOrderTransitions.target(Action.ASSIGN, Status.ASSIGNED));
        assertThrows(BusinessException.class, () -> WorkOrderTransitions.target(Action.ASSIGN, Status.PROCESSING));
        assertThrows(BusinessException.class, () -> WorkOrderTransitions.target(Action.ASSIGN, Status.COMPLETED));
    }

    @Test
    void startOnlyFromAssigned() {
        assertEquals(Status.PROCESSING, WorkOrderTransitions.target(Action.START, Status.ASSIGNED));
        assertThrows(BusinessException.class, () -> WorkOrderTransitions.target(Action.START, Status.PENDING_DISPATCH));
    }

    @Test
    void completeOnlyFromProcessing() {
        assertEquals(Status.COMPLETED, WorkOrderTransitions.target(Action.COMPLETE, Status.PROCESSING));
        assertThrows(BusinessException.class, () -> WorkOrderTransitions.target(Action.COMPLETE, Status.ASSIGNED));
    }

    @Test
    void rejectAllowedBeforeProcessing() {
        assertEquals(Status.REJECTED, WorkOrderTransitions.target(Action.REJECT, Status.PENDING_DISPATCH));
        assertEquals(Status.REJECTED, WorkOrderTransitions.target(Action.REJECT, Status.ASSIGNED));
        assertThrows(BusinessException.class, () -> WorkOrderTransitions.target(Action.REJECT, Status.PROCESSING));
    }

    @Test
    void cancelAllowedBeforeCompleted() {
        for (Status status : List.of(Status.PENDING_DISPATCH, Status.ASSIGNED, Status.PROCESSING)) {
            assertEquals(Status.CANCELLED, WorkOrderTransitions.target(Action.CANCEL, status));
        }
        assertThrows(BusinessException.class, () -> WorkOrderTransitions.target(Action.CANCEL, Status.COMPLETED));
        assertThrows(BusinessException.class, () -> WorkOrderTransitions.target(Action.CANCEL, Status.REJECTED));
    }
}
