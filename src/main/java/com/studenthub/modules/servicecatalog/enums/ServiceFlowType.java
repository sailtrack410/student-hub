package com.studenthub.modules.servicecatalog.enums;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;

/** Persisted routing policy. Unknown or unconfigured services never implicitly dispatch repairs. */
public enum ServiceFlowType {
    REPAIR, LEAVE_APPROVAL, MANUAL, GUIDE;

    public static ServiceFlowType resolve(String value) {
        if (value == null || value.isBlank()) return MANUAL;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "未知服务流程类型：" + value);
        }
    }
}
