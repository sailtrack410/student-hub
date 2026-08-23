package com.studenthub.modules.identity.security;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "数据范围：SELF 本人、ASSIGNED 被指派、ORGANIZATION 本组织、ORGANIZATION_AND_CHILDREN 本组织及下级、CAMPUS 校区、ALL 全部")
public enum DataScope {
    SELF,
    ASSIGNED,
    ORGANIZATION,
    ORGANIZATION_AND_CHILDREN,
    CAMPUS,
    ALL
}
