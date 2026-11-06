package com.studenthub.modules.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrderEntity> {

    String REPAIR_REQUEST_IDS = "SELECT id FROM service_request WHERE flow_type = 'REPAIR' AND deleted = 0";

    @Select("SELECT * FROM work_order WHERE request_id = #{requestId} AND deleted = 0 LIMIT 1")
    WorkOrderEntity findByRequestId(@Param("requestId") Long requestId);

    /**
     * 组织自身及全部下级组织 ID（含本组织），用于调度员数据范围过滤。
     */
    @Select("""
            WITH RECURSIVE org_tree (id) AS (
                SELECT id FROM sys_organization WHERE id = #{organizationId} AND deleted = 0
                UNION ALL
                SELECT o.id FROM sys_organization o
                    JOIN org_tree t ON o.parent_id = t.id
                 WHERE o.deleted = 0
            )
            SELECT id FROM org_tree
            """)
    List<Long> selectSelfAndDescendantOrgIds(@Param("organizationId") Long organizationId);
}
