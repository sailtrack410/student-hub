package com.studenthub.modules.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_storage")
public class FileStorageEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 落盘文件名（UUID + 扩展名），不含路径 */
    private String storageKey;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private Long uploaderId;
}
