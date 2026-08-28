package com.studenthub.modules.servicerequest;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.servicerequest.statemachine.RequestTransitions;
import com.studenthub.modules.servicerequest.statemachine.RequestTransitions.Action;
import com.studenthub.modules.servicerequest.statemachine.RequestTransitions.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestTransitionsTest {

    @Test
    void submitStartsFromNothing() {
        assertEquals(Status.SUBMITTED, RequestTransitions.target(Action.SUBMIT, null));
    }

    @Test
    void dispatchOnlyFromSubmitted() {
        assertEquals(Status.PROCESSING, RequestTransitions.target(Action.DISPATCH, Status.SUBMITTED));
        assertThrows(BusinessException.class, () -> RequestTransitions.target(Action.DISPATCH, Status.PROCESSING));
        assertThrows(BusinessException.class, () -> RequestTransitions.target(Action.DISPATCH, Status.COMPLETED));
    }

    @Test
    void completeOnlyFromProcessing() {
        assertEquals(Status.COMPLETED, RequestTransitions.target(Action.COMPLETE, Status.PROCESSING));
        assertThrows(BusinessException.class, () -> RequestTransitions.target(Action.COMPLETE, Status.SUBMITTED));
    }

    @Test
    void cancelAllowedBeforeCompleted() {
        assertEquals(Status.CANCELLED, RequestTransitions.target(Action.CANCEL, Status.SUBMITTED));
        assertEquals(Status.CANCELLED, RequestTransitions.target(Action.CANCEL, Status.PROCESSING));
        assertThrows(BusinessException.class, () -> RequestTransitions.target(Action.CANCEL, Status.COMPLETED));
        assertThrows(BusinessException.class, () -> RequestTransitions.target(Action.CANCEL, Status.CANCELLED));
    }

    @Test
    void evaluateOnlyOnCompleted() {
        assertEquals(Status.COMPLETED, RequestTransitions.target(Action.EVALUATE, Status.COMPLETED));
        assertThrows(BusinessException.class, () -> RequestTransitions.target(Action.EVALUATE, Status.PROCESSING));
    }

    @Test
    void rejectFromSubmittedOrProcessing() {
        assertEquals(Status.REJECTED, RequestTransitions.target(Action.REJECT, Status.SUBMITTED));
        assertEquals(Status.REJECTED, RequestTransitions.target(Action.REJECT, Status.PROCESSING));
        assertThrows(BusinessException.class, () -> RequestTransitions.target(Action.REJECT, Status.CANCELLED));
    }

    @Test
    void unknownStatusTextRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> RequestTransitions.parse("NOT_A_STATUS"));
        assertEquals(ErrorCode.REQUEST_STATE_INVALID.getCode(), exception.getCode());
    }
}
