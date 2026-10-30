package com.studenthub.modules.dashboard.controller.dto;

import com.studenthub.modules.dashboard.entity.ServiceHolidayEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "节假日/工作日历")
public record HolidayResponse(
        @Schema(description = "ID") Long id,
        @Schema(description = "校区 ID，0 为平台级") Long campusId,
        @Schema(description = "日期") LocalDate holidayDate,
        @Schema(description = "名称") String holidayName,
        @Schema(description = "是否工作日") boolean workday,
        @Schema(description = "备注") String remark,
        @Schema(description = "创建时间") LocalDateTime createdAt) {

    public static HolidayResponse from(ServiceHolidayEntity entity) {
        return new HolidayResponse(entity.getId(), entity.getCampusId(), entity.getHolidayDate(),
                entity.getHolidayName(), Boolean.TRUE.equals(entity.getIsWorkday()), entity.getRemark(),
                entity.getCreatedAt());
    }
}
