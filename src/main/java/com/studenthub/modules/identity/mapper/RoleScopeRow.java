package com.studenthub.modules.identity.mapper;

import com.studenthub.modules.identity.security.DataScope;
import lombok.Data;

@Data
public class RoleScopeRow {

    private String roleCode;
    private Long organizationId;
    private DataScope dataScope;
}
