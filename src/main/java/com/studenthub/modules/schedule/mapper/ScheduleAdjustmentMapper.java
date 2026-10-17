package com.studenthub.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.schedule.entity.ScheduleAdjustmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ScheduleAdjustmentMapper extends BaseMapper<ScheduleAdjustmentEntity> {

    @Select("SELECT * FROM schedule_adjustment WHERE semester_code = #{semesterCode} AND deleted = 0 ORDER BY notice_time DESC")
    List<ScheduleAdjustmentEntity> selectBySemester(@Param("semesterCode") String semesterCode);

    @Select("SELECT COUNT(*) FROM schedule_adjustment WHERE semester_code = #{semesterCode} AND deleted = 0")
    long countBySemester(@Param("semesterCode") String semesterCode);
}
