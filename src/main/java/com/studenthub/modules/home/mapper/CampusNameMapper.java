package com.studenthub.modules.home.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CampusNameMapper {

    @Select("SELECT campus_name FROM sys_campus WHERE id = #{campusId}")
    String nameOf(@Param("campusId") Long campusId);
}
