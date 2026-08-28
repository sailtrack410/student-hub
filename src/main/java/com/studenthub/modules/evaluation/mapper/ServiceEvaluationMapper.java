package com.studenthub.modules.evaluation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.evaluation.entity.ServiceEvaluationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ServiceEvaluationMapper extends BaseMapper<ServiceEvaluationEntity> {

    @Select("SELECT * FROM service_evaluation WHERE request_id = #{requestId} AND deleted = 0 LIMIT 1")
    ServiceEvaluationEntity selectByRequestId(@Param("requestId") Long requestId);
}
