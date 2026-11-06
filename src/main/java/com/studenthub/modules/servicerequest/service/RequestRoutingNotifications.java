package com.studenthub.modules.servicerequest.service;

import com.studenthub.modules.adminscope.security.AdminDataScope;
import com.studenthub.modules.adminscope.security.AdminDataScopeService;
import com.studenthub.modules.identity.mapper.IdentityAccessMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.RoleScope;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.RequestRecipientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/** 使用既有权限和组织数据范围定位受理人，不推测师生审批关系。 */
@Service
@RequiredArgsConstructor
public class RequestRoutingNotifications {
    private final RequestRecipientMapper recipientMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final AdminDataScopeService scopeService;
    private final NotificationService notificationService;

    public int notifyQueue(ServiceRequestEntity request, String title, String content) {
        String permission = RequestBusinessView.repair(request) ? "work-order:dispatch" : "catalog:manage";
        int sent = 0;
        for (Long userId : recipientMapper.candidates(request.getCampusId(), permission)) {
            if (userId.equals(request.getRequesterId())) continue;
            List<RoleScope> scopes = identityAccessMapper.findRoleScopes(userId).stream()
                    .map(row -> new RoleScope(row.getRoleCode(), row.getOrganizationId(), row.getDataScope())).toList();
            AuthenticatedUser candidate = new AuthenticatedUser(userId, request.getCampusId(), null, null, null,
                    null, null, scopes, identityAccessMapper.findPermissionCodes(userId), null, null);
            AdminDataScope scope = scopeService.resolve(candidate);
            if (!scope.unrestricted() && !scope.allowsOrganization(request.getHandlerOrganizationId())) continue;
            notificationService.send(new NotificationSendCommand(userId, "REQUEST", title, content,
                    "SERVICE_REQUEST", request.getId()));
            sent++;
        }
        return sent;
    }
}
