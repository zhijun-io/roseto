package io.zhijun.roseto.domain.oidc;

import java.time.Instant;

public record RefreshGrant(String userId, String clientId, String tenantId, String scope, String resource, Instant expiresAt) {}
