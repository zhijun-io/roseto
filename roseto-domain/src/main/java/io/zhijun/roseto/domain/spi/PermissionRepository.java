package io.zhijun.roseto.domain.spi;

import io.zhijun.roseto.domain.api.Permission;

import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository {
    Permission save(Permission permission);
    Optional<Permission> findByTenantResourceAndName(UUID tenantId, UUID resourceId, String name);
}
