package io.zhijun.roseto.oidc;

import io.zhijun.roseto.domain.oidc.RefreshGrant;
import io.zhijun.roseto.domain.spi.RefreshTokenRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RefreshTokenService {
    private final SecureRandom random = new SecureRandom();
    private final RefreshTokenRepository repository;
    private final Map<String, Grant> active = new ConcurrentHashMap<>();
    private final java.util.Set<String> revokedFamilies = ConcurrentHashMap.newKeySet();
    public RefreshTokenService() { repository = null; }
    public RefreshTokenService(RefreshTokenRepository repository) { this.repository = repository; }
    public String issue(Grant grant) {
        String token = random(); var expires = Instant.now().plusSeconds(30L * 24 * 3600);
        if (repository == null) active.put(token, grant); else repository.save(token, new RefreshGrant(grant.userId(), grant.clientId(), grant.tenantId(), grant.scope(), grant.resource(), expires));
        return token;
    }
    public synchronized Rotation rotate(String token, String clientId, String tenantId) {
        var grant = repository == null
            ? consumeInMemory(token, clientId, tenantId)
            : repository.consume(token, clientId, tenantId)
                .map(g -> new Grant(g.userId(), g.clientId(), g.tenantId(), g.scope(), g.resource()))
                .orElse(null);
        if (grant == null) {
            throw new IllegalArgumentException((repository == null ? revokedFamilies.contains(token) : repository.wasUsed(token))
                ? "refresh token replay" : "invalid or expired refresh token");
        }
        return new Rotation(issue(grant), grant);
    }
    private Grant consumeInMemory(String token, String clientId, String tenantId) {
        var grant = active.get(token);
        if (grant == null || !grant.clientId().equals(clientId) || !grant.tenantId().equals(tenantId)) return null;
        active.remove(token);
        revokedFamilies.add(token);
        return grant;
    }
    public void revoke(String token) { if (repository == null) { active.remove(token); revokedFamilies.add(token); } else repository.revoke(token); }
    public void revokeByClient(String clientId) { if (repository == null) active.entrySet().removeIf(e -> e.getValue().clientId().equals(clientId)); else repository.revokeByClient(clientId); }
    private String random() { byte[] b = new byte[32]; random.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    public record Grant(String userId, String clientId, String tenantId, String scope, String resource) {}
    public record Rotation(String refreshToken, Grant grant) {}
}
