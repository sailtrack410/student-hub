package com.studenthub.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.schedule.entity.ScheduleClassCourseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScheduleClassCourseMapper extends BaseMapper<ScheduleClassCourseEntity> {

    @Select("SELECT * FROM schedule_class_course WHERE class_code = #{classCode} AND semester_code = #{semesterCode} AND deleted = 0")
    java.util.List<ScheduleClassCourseEntity> selectByClassAndSemester(@Param("classCode") String classCode,
                                                                       @Param("semesterCode") String semesterCode);

    @Delete("DELETE FROM schedule_class_course WHERE class_code = #{classCode} AND semester_code = #{semesterCode}")
    int deleteByClassAndSemester(@Param("classCode") String classCode, @Param("semesterCode") String semesterCode);

    @Insert("<script>" +
            "INSERT INTO schedule_class_course (id, class_code, semester_code, semester_name, course_code, course_name, teacher, course_nature, location, day_of_week, start_section, end_section, weeks, week_parity, created_at, updated_at, created_by, updated_by, deleted, version) VALUES " +
            "<foreach collection='list' item='e' separator=','>" +
            "(#{e.id}, #{e.classCode}, #{e.semesterCode}, #{e.semesterName}, #{e.courseCode}, #{e.courseName}, #{e.teacher}, #{e.courseNature}, #{e.location}, #{e.dayOfWeek}, #{e.startSection}, #{e.endSection}, #{e.weeks}, #{e.weekParity}, NOW(), NOW(), #{e.createdBy}, #{e.updatedBy}, 0, 0)" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") java.util.List<ScheduleClassCourseEntity> list);
}