package io.zhijun.roseto.domain.session;

import java.time.Instant;
import java.util.UUID;

public record UserSession(UUID id, UUID tenantId, UUID userId, String device,
                          Instant createdAt, Instant lastSeenAt, Instant revokedAt) {
    public boolean active() { return revokedAt == null; }
    public UserSession revoke(Instant now) {
        if (!active()) return this;
        return new UserSession(id, tenantId, userId, device, createdAt, lastSeenAt, now);
    }
}
