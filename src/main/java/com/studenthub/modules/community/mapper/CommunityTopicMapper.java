package com.studenthub.modules.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.community.entity.CommunityTopicEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommunityTopicMapper extends BaseMapper<CommunityTopicEntity> {
    @Update("""
            UPDATE community_topic
            SET heat = COALESCE(heat, 0) + 1,
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE id = #{id} AND deleted = 0
            """)
    int incrementHeat(@Param("id") Long id);
}
