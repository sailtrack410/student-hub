package com.studenthub.modules.appversion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.appversion.entity.AppVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppVersionMapper extends BaseMapper<AppVersionEntity> {

    @Insert("INSERT IGNORE INTO app_release_channel(platform, channel) VALUES(#{platform}, #{channel})")
    int ensureReleaseChannel(@Param("platform") String platform, @Param("channel") String channel);

    @Select("SELECT channel FROM app_release_channel WHERE platform = #{platform} AND channel = #{channel} FOR UPDATE")
    String lockReleaseChannel(@Param("platform") String platform, @Param("channel") String channel);

    @Update("UPDATE app_version SET download_count = download_count + 1 WHERE id = #{id}")
    int incrementDownloadCount(@Param("id") Long id);
}
