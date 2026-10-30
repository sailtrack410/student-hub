package com.studenthub.modules.homework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.homework.entity.HomeworkEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HomeworkMapper extends BaseMapper<HomeworkEntity> {
}
