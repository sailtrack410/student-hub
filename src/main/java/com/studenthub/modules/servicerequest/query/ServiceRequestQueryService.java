package com.studenthub.modules.servicerequest.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceRequestQueryService implements ServiceRequestQuery {
    private final ServiceRequestMapper mapper;

    @Override
    public List<Request> recentForUser(Long userId, StatusFilter status, int limit) {
        if (userId == null) return List.of();
        LambdaQueryWrapper<ServiceRequestEntity> query = new LambdaQueryWrapper<ServiceRequestEntity>()
                .select(ServiceRequestEntity::getId, ServiceRequestEntity::getItemName,
                        ServiceRequestEntity::getTitle, ServiceRequestEntity::getStatus,
                        ServiceRequestEntity::getRequestNo, ServiceRequestEntity::getSubmittedAt)
                .eq(ServiceRequestEntity::getRequesterId, userId)
                .orderByDesc(ServiceRequestEntity::getSubmittedAt)
                .orderByDesc(ServiceRequestEntity::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 20)));
        if (status == StatusFilter.IN_PROGRESS) {
            query.in(ServiceRequestEntity::getStatus, List.of("SUBMITTED", "PROCESSING"));
        } else if (status == StatusFilter.COMPLETED) {
            query.eq(ServiceRequestEntity::getStatus, "COMPLETED");
        }
        return mapper.selectList(query).stream()
                .map(r -> new Request(r.getId(), r.getItemName(), r.getTitle(), r.getStatus(),
                        r.getRequestNo(), r.getSubmittedAt()))
                .toList();
    }
}
