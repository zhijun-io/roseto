package io.zhijun.roseto.oidc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import io.zhijun.roseto.domain.oidc.RefreshGrant;
import io.zhijun.roseto.domain.spi.RefreshTokenRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;

class RefreshTokenServiceTest {
    @Test void rotationInvalidatesOldTokenAndReplayFails() {
        var service = new RefreshTokenService();
        var token = service.issue(new RefreshTokenService.Grant("user", "client", "tenant", "openid", null));
        var next = service.rotate(token, "client", "tenant");
        assertNotEquals(token, next.refreshToken());
        assertEquals("user", next.grant().userId());
        assertThrows(IllegalArgumentException.class, () -> service.rotate(token, "client", "tenant"));
    }
    @Test void wrongClientCannotConsumeRefreshToken() {
        var service = new RefreshTokenService();
        var token = service.issue(new RefreshTokenService.Grant("user", "client", "tenant", "openid", null));
        assertThrows(IllegalArgumentException.class, () -> service.rotate(token, "other-client", "tenant"));
        var rotation = service.rotate(token, "client", "tenant");
        assertEquals("user", rotation.grant().userId());
    }

    @Test void expiredPersistentTokenCannotBeRotated() {
        var tokens = new HashMap<String, RefreshGrant>();
        var used = new HashSet<String>();
        RefreshTokenRepository repository = new RefreshTokenRepository() {
            public void save(String token, RefreshGrant grant) { tokens.put(token, grant); }
            public java.util.Optional<RefreshGrant> consume(String token, String clientId, String tenantId) {
                var grant = tokens.get(token);
                if (grant != null && grant.clientId().equals(clientId) && grant.tenantId().equals(tenantId)
                    && grant.expiresAt().isAfter(Instant.now())) {
                    tokens.remove(token);
                    used.add(token);
                    return java.util.Optional.of(grant);
                }
                return java.util.Optional.empty();
            }
            public boolean wasUsed(String token) { return used.contains(token); }
            public void revokeByClient(String clientId) {}
            public void revoke(String token) { tokens.remove(token); used.add(token); }
        };
        var service = new RefreshTokenService(repository);
        repository.save("expired", new RefreshGrant("user", "client", "tenant", "openid", null,
            Instant.now().minusSeconds(1)));

        var error = assertThrows(IllegalArgumentException.class, () -> service.rotate("expired", "client", "tenant"));
        assertEquals("invalid or expired refresh token", error.getMessage());
    }
}
