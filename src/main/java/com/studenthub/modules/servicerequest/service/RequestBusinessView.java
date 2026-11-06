package com.studenthub.modules.servicerequest.service;

import com.studenthub.modules.servicecatalog.enums.ServiceFlowType;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import java.util.List;

/** 学生端业务状态和动作契约的统一来源；仅使用申请快照。 */
public final class RequestBusinessView {
    private RequestBusinessView() { }

    public static boolean repair(ServiceRequestEntity request) {
        return ServiceFlowType.resolve(request.getFlowType()) == ServiceFlowType.REPAIR;
    }

    public static boolean leave(ServiceRequestEntity request) {
        return ServiceFlowType.resolve(request.getFlowType()) == ServiceFlowType.LEAVE_APPROVAL;
    }

    public static String flowType(ServiceRequestEntity request) {
        return ServiceFlowType.resolve(request.getFlowType()).name();
    }

    public static String status(ServiceRequestEntity request, String orderStatus) {
        if (leave(request)) {
            if (request.getLeaveCancelAt() != null) return "CLOSED";
            return switch (request.getStatus()) {
                case "SUBMITTED" -> "PENDING_APPROVAL";
                case "PROCESSING" -> "APPROVED";
                case "COMPLETED" -> "COMPLETED";
                default -> request.getStatus();
            };
        }
        if (repair(request) && orderStatus != null) return orderStatus;
        return "SUBMITTED".equals(request.getStatus()) ? "PENDING_ACCEPTANCE" : request.getStatus();
    }

    public static String label(ServiceRequestEntity request, String orderStatus) {
        return switch (status(request, orderStatus)) {
            case "PENDING_APPROVAL" -> "待审批";
            case "APPROVED" -> "已批准，待销假";
            case "CLOSED" -> "已销假";
            case "PENDING_ACCEPTANCE" -> repair(request) ? "待派单" : "待人工受理";
            case "PENDING_DISPATCH" -> "待派单";
            case "ASSIGNED" -> "已派单";
            case "PROCESSING" -> "处理中";
            case "ONSITE_PHOTO_RECORDED" -> "维修处理中";
            case "PENDING_CONFIRMATION" -> "待验收";
            case "COMPLETED" -> leave(request) ? "已办结" : "已完成";
            case "REJECTED" -> "已驳回";
            case "CANCELLED" -> "已撤回";
            default -> request.getStatus();
        };
    }

    public static List<String> actions(ServiceRequestEntity request, String orderStatus, boolean evaluated) {
        if (leave(request)) {
            return "SUBMITTED".equals(request.getStatus()) ? List.of("CANCEL", "URGE") : List.of();
        }
        if ("COMPLETED".equals(request.getStatus())) {
            return repair(request) && !evaluated ? List.of("EVALUATE") : List.of();
        }
        if (!List.of("SUBMITTED", "PROCESSING").contains(request.getStatus())) return List.of();
        if (repair(request) && "PENDING_CONFIRMATION".equals(orderStatus)) {
            return List.of("CONFIRM", "REJECT_COMPLETION", "CANCEL", "URGE");
        }
        return List.of("CANCEL", "URGE");
    }
}
