package io.zhijun.roseto.domain.spi;

import io.zhijun.roseto.domain.api.Role;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {
    Role save(Role role);
    Optional<Role> findByTenantAndName(UUID tenantId, String name);
    boolean hasPermission(UUID roleId, UUID permissionId);
    void addPermission(UUID roleId, UUID permissionId);
}
