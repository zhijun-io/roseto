package io.zhijun.roseto.domain.user;

import java.time.Instant;
import java.util.UUID;

public record User(UUID id, UUID tenantId, String username, String passwordHash,
                   boolean enabled, Instant createdAt) {
}
