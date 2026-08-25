package io.zhijun.roseto.domain.spi;

import io.zhijun.roseto.domain.user.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);
    Optional<User> findByTenantAndUsername(UUID tenantId, String username);
    Optional<User> findById(UUID id);
    void updateEnabled(UUID userId, boolean enabled);
    boolean hasRole(UUID userId, UUID roleId);
    void addRole(UUID userId, UUID roleId);
    void removeRole(UUID userId, UUID roleId);
}
