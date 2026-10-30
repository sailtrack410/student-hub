package com.studenthub.modules.push.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.push.entity.PushTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PushTaskMapper extends BaseMapper<PushTaskEntity> {
    @Update("UPDATE push_task SET status='SENDING', execution_token=#{token}, started_at=CURRENT_TIMESTAMP, "
            + "finished_at=NULL, last_error=NULL, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=#{task.id} AND campus_id=#{task.campusId} "
            + "AND deleted=0 AND status=#{task.status} AND version=#{task.version}")
    int claim(@Param("task") PushTaskEntity task, @Param("token") String token);

    @Update("UPDATE push_task SET status=#{task.status}, total_count=#{task.totalCount}, success_count=#{task.successCount}, "
            + "failed_count=#{task.failedCount}, delivered_count=#{task.deliveredCount}, clicked_count=#{task.clickedCount}, "
            + "skipped_count=#{task.skippedCount}, not_configured_count=#{task.notConfiguredCount}, retry_count=#{task.retryCount}, "
            + "last_error=#{task.lastError}, finished_at=#{task.finishedAt}, execution_token=NULL, version=version+1 "
            + "WHERE id=#{task.id} AND deleted=0 AND status='SENDING' AND execution_token=#{task.executionToken}")
    int finishOwned(@Param("task") PushTaskEntity task);

    @Update("UPDATE push_task SET deleted=1, version=version+1 WHERE id=#{task.id} AND deleted=0 "
            + "AND status=#{task.status} AND status<>'SENDING' AND version=#{task.version}")
    int deleteIdle(@Param("task") PushTaskEntity task);

    @Update("UPDATE push_task SET updated_at=CURRENT_TIMESTAMP WHERE id=#{task.id} AND deleted=0 "
            + "AND status='SENDING' AND execution_token=#{task.executionToken}")
    int touchOwned(@Param("task") PushTaskEntity task);

    @Update("UPDATE push_task SET status='FAILED', execution_token=NULL, finished_at=CURRENT_TIMESTAMP, "
            + "last_error='执行中断；PENDING 投递请核对渠道回执后处理，失败记录可重试', version=version+1 "
            + "WHERE deleted=0 AND status='SENDING' AND updated_at<#{cutoff}")
    int recoverInterrupted(@Param("cutoff") java.time.LocalDateTime cutoff);
}
