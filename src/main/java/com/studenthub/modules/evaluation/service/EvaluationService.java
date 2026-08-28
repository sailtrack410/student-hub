package com.studenthub.modules.evaluation.service;

import com.studenthub.modules.evaluation.controller.dto.CreateEvaluationRequest;
import com.studenthub.modules.evaluation.controller.dto.EvaluationResponse;
import com.studenthub.modules.identity.security.AuthenticatedUser;

public interface EvaluationService {

    EvaluationResponse submit(AuthenticatedUser evaluator, Long requestId, CreateEvaluationRequest request);
}
