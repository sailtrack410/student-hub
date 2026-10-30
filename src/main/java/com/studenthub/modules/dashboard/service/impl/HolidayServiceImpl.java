package com.studenthub.modules.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.dashboard.controller.dto.HolidayRequest;
import com.studenthub.modules.dashboard.controller.dto.HolidayResponse;
import com.studenthub.modules.dashboard.entity.ServiceHolidayEntity;
import com.studenthub.modules.dashboard.mapper.ServiceHolidayMapper;
import com.studenthub.modules.dashboard.service.HolidayService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayServiceImpl implements HolidayService {

    private final ServiceHolidayMapper holidayMapper;
    private final AdminCampusContextService campusContextService;

    @Override
    public List<HolidayResponse> list(Integer year) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        if (targetYear < 2000 || targetYear > 2100) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "年份需在 2000-2100 之间");
        }
        LocalDate start = LocalDate.of(targetYear, 1, 1);
        LocalDate end = LocalDate.of(targetYear, 12, 31);
        Long campusId = campusContextService.currentCampusId();
        List<ServiceHolidayEntity> rows = holidayMapper.selectList(new LambdaQueryWrapper<ServiceHolidayEntity>()
                .in(ServiceHolidayEntity::getCampusId, 0L, campusId)
                .between(ServiceHolidayEntity::getHolidayDate, start, end)
                .orderByAsc(ServiceHolidayEntity::getHolidayDate));
        return rows.stream().map(HolidayResponse::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HolidayResponse create(HolidayRequest request) {
        Long campusId = campusContextService.currentCampusId();
        validateUnique(campusId, request.holidayDate(), null);
        ServiceHolidayEntity entity = new ServiceHolidayEntity();
        entity.setCampusId(campusId);
        entity.setHolidayDate(request.holidayDate());
        entity.setHolidayName(request.holidayName().trim());
        entity.setIsWorkday(Boolean.TRUE.equals(request.isWorkday()));
        entity.setRemark(trimToNull(request.remark()));
        holidayMapper.insert(entity);
        log.info("新增节假日配置, campusId={}, date={}, isWorkday={}", campusId, request.holidayDate(), request.isWorkday());
        return HolidayResponse.from(holidayMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HolidayResponse update(Long id, HolidayRequest request) {
        ServiceHolidayEntity existing = requireEditable(id);
        validateUnique(existing.getCampusId(), request.holidayDate(), id);
        ServiceHolidayEntity update = new ServiceHolidayEntity();
        update.setId(existing.getId());
        update.setHolidayDate(request.holidayDate());
        update.setHolidayName(request.holidayName().trim());
        update.setIsWorkday(Boolean.TRUE.equals(request.isWorkday()));
        update.setRemark(trimToNull(request.remark()));
        update.setVersion(existing.getVersion());
        holidayMapper.updateById(update);
        return HolidayResponse.from(holidayMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ServiceHolidayEntity existing = requireEditable(id);
        holidayMapper.deleteById(existing.getId());
        log.info("删除节假日配置, id={}, date={}", id, existing.getHolidayDate());
    }

    private ServiceHolidayEntity requireEditable(Long id) {
        ServiceHolidayEntity entity = holidayMapper.selectById(id);
        Long campusId = campusContextService.currentCampusId();
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "节假日配置不存在");
        }
        if (entity.getCampusId() == null || !entity.getCampusId().equals(campusId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能维护当前校区的节假日配置");
        }
        return entity;
    }

    private void validateUnique(Long campusId, LocalDate date, Long excludeId) {
        if (date == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "日期不能为空");
        }
        LambdaQueryWrapper<ServiceHolidayEntity> wrapper = new LambdaQueryWrapper<ServiceHolidayEntity>()
                .eq(ServiceHolidayEntity::getCampusId, campusId)
                .eq(ServiceHolidayEntity::getHolidayDate, date);
        if (excludeId != null) {
            wrapper.ne(ServiceHolidayEntity::getId, excludeId);
        }
        if (holidayMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该日期已存在节假日/调休配置");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
