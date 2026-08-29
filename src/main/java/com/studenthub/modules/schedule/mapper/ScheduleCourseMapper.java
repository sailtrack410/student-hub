package com.studenthub.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.schedule.entity.ScheduleCourseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScheduleCourseMapper extends BaseMapper<ScheduleCourseEntity> {

    @Select("SELECT * FROM schedule_course WHERE user_id = #{userId} AND deleted = 0")
    java.util.List<ScheduleCourseEntity> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM schedule_course WHERE user_id = #{userId} AND semester_code = #{semesterCode} AND deleted = 0")
    java.util.List<ScheduleCourseEntity> selectByUserIdAndSemester(@Param("userId") Long userId,
                                                                   @Param("semesterCode") String semesterCode);

    @Delete("DELETE FROM schedule_course WHERE user_id = #{userId} AND semester_code = #{semesterCode}")
    int deleteByUserIdAndSemester(@Param("userId") Long userId, @Param("semesterCode") String semesterCode);

    @Insert("<script>" +
            "INSERT INTO schedule_course (id, user_id, semester_code, semester_name, course_code, course_name, teacher, class_code, course_nature, location, day_of_week, start_section, end_section, weeks, week_parity, raw_text, created_at, updated_at, created_by, updated_by, deleted, version) VALUES " +
            "<foreach collection='list' item='e' separator=','>" +
            "(#{e.id}, #{e.userId}, #{e.semesterCode}, #{e.semesterName}, #{e.courseCode}, #{e.courseName}, #{e.teacher}, #{e.classCode}, #{e.courseNature}, #{e.location}, #{e.dayOfWeek}, #{e.startSection}, #{e.endSection}, #{e.weeks}, #{e.weekParity}, #{e.rawText}, NOW(), NOW(), #{e.createdBy}, #{e.updatedBy}, 0, 0)" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") java.util.List<ScheduleCourseEntity> list);
}