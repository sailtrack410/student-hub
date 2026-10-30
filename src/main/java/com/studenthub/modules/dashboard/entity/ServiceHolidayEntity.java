package com.studenthub.modules.dashboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 节假日/工作日历：SLA 承诺截止时间按 service_item.sla_hours 与本周历推算。
 * is_workday = true 表示调休上班日（周末但计为工作日）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_holiday")
public class ServiceHolidayEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private LocalDate holidayDate;
    private String holidayName;
    private Boolean isWorkday;
    private String remark;
}
