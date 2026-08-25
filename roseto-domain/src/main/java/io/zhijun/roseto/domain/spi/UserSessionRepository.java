package io.zhijun.roseto.domain.spi;

import io.zhijun.roseto.domain.session.UserSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository {
    UserSession save(UserSession session);
    Optional<UserSession> findByIdAndUser(UUID tenantId, UUID userId, UUID sessionId);
    List<UserSession> findByUser(UUID tenantId, UUID userId);
    void revokeByUser(UUID tenantId, UUID userId, java.time.Instant revokedAt);
}
