package com.studenthub.modules.identity.campus;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.controller.dto.AdminCampusOptionResponse;
import com.studenthub.modules.identity.entity.CampusEntity;
import com.studenthub.modules.identity.mapper.CampusMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 解析并校验管理端校区上下文。
 *
 * <p>规则：平台管理员（platform:all）可切换到任意校区；其他管理员只能停留在自己的校区。
 * 请求头 X-Campus-Id 缺失时回落为当前用户所属校区。管理端没有“全部校区”的隐式写上下文，
 * 避免一次写操作落错校区；聚合页面如需要跨校区统计必须显式传 campusId（后续能力）。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminCampusContextService {

    public static final String CAMPUS_HEADER = "X-Campus-Id";

    private final CampusMapper campusMapper;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 在请求进入 Controller 前绑定上下文。
     *
     * @param requestedCampusId 请求头中的校区；为空表示使用当前用户校区
     */
    public AdminCampusContext.Snapshot activate(Long requestedCampusId) {
        AuthenticatedUser user = currentUserProvider.requireCurrentUser();
        boolean platformAdmin = (user.permissionCodes() != null
                && user.permissionCodes().contains("platform:all"))
                || (user.roleCodes() != null && user.roleCodes().contains("PLATFORM_ADMIN"));
        Long selected = requestedCampusId == null ? user.campusId() : requestedCampusId;
        if (selected == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未绑定校区，无法进入管理端");
        }
        if (requestedCampusId != null && !requestedCampusId.equals(user.campusId()) && !platformAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权切换到其他校区");
        }
        CampusEntity campus = campusMapper.selectById(selected);
        if (campus == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "校区不存在");
        }
        List<Long> accessible = accessibleCampusIds(user, platformAdmin);
        AdminCampusContext.Snapshot snapshot = new AdminCampusContext.Snapshot(
                selected, user.userId(), platformAdmin, accessible);
        AdminCampusContext.bind(snapshot);
        return snapshot;
    }

    public AdminCampusContext.Snapshot snapshot() {
        AdminCampusContext.Snapshot snapshot = AdminCampusContext.current();
        if (snapshot != null) {
            return snapshot;
        }
        return activate(null);
    }

    public Long currentCampusId() {
        return snapshot().campusId();
    }

    public boolean isPlatformAdmin() {
        return snapshot().platformAdmin();
    }

    public List<AdminCampusOptionResponse> accessibleCampuses() {
        AdminCampusContext.Snapshot snapshot = snapshot();
        LambdaQueryWrapper<CampusEntity> wrapper = new LambdaQueryWrapper<CampusEntity>()
                .in(CampusEntity::getId, snapshot.accessibleCampusIds())
                .orderByAsc(CampusEntity::getId);
        return campusMapper.selectList(wrapper).stream()
                .map(item -> new AdminCampusOptionResponse(item.getId(), item.getCampusCode(),
                        item.getCampusName(), item.getStatus()))
                .toList();
    }

    public CampusEntity currentCampus() {
        CampusEntity campus = campusMapper.selectById(currentCampusId());
        if (campus == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "校区不存在");
        }
        return campus;
    }

    private List<Long> accessibleCampusIds(AuthenticatedUser user, boolean platformAdmin) {
        if (platformAdmin) {
            return campusMapper.selectList(new LambdaQueryWrapper<CampusEntity>()
                            .orderByAsc(CampusEntity::getId))
                    .stream().map(CampusEntity::getId).toList();
        }
        return user.campusId() == null ? List.of() : List.of(user.campusId());
    }
}
