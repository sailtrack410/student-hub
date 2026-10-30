package com.studenthub.modules.community.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 社区账号与官方抓取来源的关联只读查询。
 * 账号类型变更需要确认该账号是否已绑定 official_feed_source（自动抓取号必须保持 OFFICIAL_AUTO）；
 * 为避免 community 与 officialfeed 模块互相依赖实体/Mapper，这里用只读 SQL 做一次轻量校验。
 */
@Mapper
public interface CommunityAccountReferenceMapper {

    @Select("SELECT COUNT(*) FROM official_feed_source WHERE author_id = #{userId} AND deleted = 0")
    long countFeedSourcesByAuthor(@Param("userId") Long userId);
}
