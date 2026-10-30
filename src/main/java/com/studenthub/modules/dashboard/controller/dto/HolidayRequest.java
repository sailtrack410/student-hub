package com.studenthub.modules.dashboard.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "节假日/工作日历维护")
public record HolidayRequest(
        @Schema(description = "日期") @NotNull LocalDate holidayDate,
        @Schema(description = "名称，如 国庆节 / 调休上班") @NotBlank @Size(max = 64) String holidayName,
        @Schema(description = "是否工作日：false 节假日 / true 调休上班") Boolean isWorkday,
        @Schema(description = "备注") @Size(max = 255) String remark) {
}
