package com.studenthub.modules.servicecatalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.servicecatalog.entity.ServiceCategoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServiceCategoryMapper extends BaseMapper<ServiceCategoryEntity> {
}
