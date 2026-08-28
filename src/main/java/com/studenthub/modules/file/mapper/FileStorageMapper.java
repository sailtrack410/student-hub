package com.studenthub.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.file.entity.FileStorageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileStorageMapper extends BaseMapper<FileStorageEntity> {
}
