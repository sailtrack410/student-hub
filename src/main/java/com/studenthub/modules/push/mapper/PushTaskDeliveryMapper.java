package com.studenthub.modules.push.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.push.entity.PushTaskDeliveryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PushTaskDeliveryMapper extends BaseMapper<PushTaskDeliveryEntity> {

    /**
     * 按渠道 × 状态聚合投递统计（N04 触达统计）。
     * 返回列：channel / status / cnt；列名大小写随数据库驱动，调用方需容错读取。
     */
    @Select("SELECT channel, status, COUNT(*) AS cnt FROM push_task_delivery "
            + "WHERE campus_id = #{campusId} AND created_at >= #{from} AND created_at <= #{to} "
            + "GROUP BY channel, status")
    List<Map<String, Object>> countByChannelStatus(@Param("campusId") Long campusId,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);
}
