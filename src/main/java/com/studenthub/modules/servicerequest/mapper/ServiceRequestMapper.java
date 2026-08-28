package com.studenthub.modules.servicerequest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServiceRequestMapper extends BaseMapper<ServiceRequestEntity> {
}
