package com.studenthub.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.schedule.entity.ScheduleSemesterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ScheduleSemesterMapper extends BaseMapper<ScheduleSemesterEntity> {

    @Select("SELECT * FROM schedule_semester WHERE user_id = #{userId} AND deleted = 0 ORDER BY semester_code DESC")
    List<ScheduleSemesterEntity> selectByUserId(@Param("userId") Long userId);
}