package com.studenthub.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.controller.dto.AdminCampusRequest;
import com.studenthub.modules.identity.controller.dto.AdminCampusResponse;
import com.studenthub.modules.identity.entity.CampusEntity;
import com.studenthub.modules.identity.mapper.CampusMapper;
import com.studenthub.modules.identity.mapper.OrganizationMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.entity.OrganizationEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.service.AdminCampusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCampusServiceImpl implements AdminCampusService {

    private final CampusMapper campusMapper;
    private final OrganizationMapper organizationMapper;
    private final UserMapper userMapper;

    @Override
    public List<AdminCampusResponse> list() {
        return campusMapper.selectList(new LambdaQueryWrapper<CampusEntity>()
                        .orderByAsc(CampusEntity::getId))
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminCampusResponse create(Long operatorId, AdminCampusRequest request) {
        String code = normalizeCode(request.campusCode());
        Long duplicate = campusMapper.selectCount(new LambdaQueryWrapper<CampusEntity>()
                .eq(CampusEntity::getCampusCode, code));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "校区编码已存在");
        }
        CampusEntity entity = new CampusEntity();
        entity.setCampusCode(code);
        entity.setCampusName(request.campusName().trim());
        entity.setStatus(normalizeStatus(request.status(), true));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        campusMapper.insert(entity);
        log.info("校区已创建, campusId={}, code={}, operatorId={}", entity.getId(), code, operatorId);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminCampusResponse update(Long operatorId, Long campusId, AdminCampusRequest request) {
        CampusEntity current = campusMapper.selectById(campusId);
        if (current == null) throw new BusinessException(ErrorCode.NOT_FOUND, "校区不存在");
        String code = normalizeCode(request.campusCode());
        Long duplicate = campusMapper.selectCount(new LambdaQueryWrapper<CampusEntity>()
                .eq(CampusEntity::getCampusCode, code)
                .ne(CampusEntity::getId, campusId));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "校区编码已存在");
        }
        String status = normalizeStatus(request.status(), false);
        if ("DISABLED".equals(status) && "ENABLED".equals(current.getStatus())) {
            Long enabled = campusMapper.selectCount(new LambdaQueryWrapper<CampusEntity>()
                    .eq(CampusEntity::getStatus, "ENABLED")
                    .ne(CampusEntity::getId, campusId));
            if (enabled == null || enabled == 0) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "至少需要保留一个启用中的校区");
            }
        }
        CampusEntity update = new CampusEntity();
        update.setId(campusId);
        update.setCampusCode(code);
        update.setCampusName(request.campusName().trim());
        update.setStatus(status);
        update.setUpdatedAt(LocalDateTime.now());
        update.setUpdatedBy(operatorId);
        update.setVersion(current.getVersion());
        campusMapper.updateById(update);
        log.info("校区已更新, campusId={}, operatorId={}", campusId, operatorId);
        return toResponse(campusMapper.selectById(campusId));
    }

    private AdminCampusResponse toResponse(CampusEntity entity) {
        Long orgCount = organizationMapper.selectCount(new LambdaQueryWrapper<OrganizationEntity>()
                .eq(OrganizationEntity::getCampusId, entity.getId()));
        Long userCount = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getCampusId, entity.getId()));
        return AdminCampusResponse.from(entity, orgCount, userCount);
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private String normalizeStatus(String status, boolean defaultEnabled) {
        String value = status == null ? "" : status.trim().toUpperCase();
        if (value.isEmpty() && defaultEnabled) return "ENABLED";
        if (!"ENABLED".equals(value) && !"DISABLED".equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "状态仅支持 ENABLED/DISABLED");
        }
        return value;
    }
}
