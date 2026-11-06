package com.studenthub.modules.servicerequest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServiceRequestMapper extends BaseMapper<ServiceRequestEntity> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM service_request WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ServiceRequestEntity lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
