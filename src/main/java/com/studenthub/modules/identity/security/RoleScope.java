package com.studenthub.modules.identity.security;

public record RoleScope(String roleCode, Long organizationId, DataScope dataScope) {
}
