package io.zhijun.roseto.domain.spi;

import io.zhijun.roseto.domain.oidc.RefreshGrant;
import java.util.Optional;

public interface RefreshTokenRepository {
    void save(String token, RefreshGrant grant);
    Optional<RefreshGrant> consume(String token, String clientId, String tenantId);
    boolean wasUsed(String token);
    void revokeByClient(String clientId);
    void revoke(String token);
}
