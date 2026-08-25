package io.zhijun.roseto.domain.interaction;

import java.time.Instant;
import java.util.UUID;

public record ExperienceInteraction(UUID id, UUID tenantId, String identifier,
                                    String mode, Status status, Instant createdAt,
                                    Instant updatedAt, String clientId, String redirectUri,
                                    String codeChallenge, String nonce, String scope, String resource) {
    public ExperienceInteraction(UUID id, UUID tenantId, String identifier, String mode, Status status,
                                 Instant createdAt, Instant updatedAt) {
        this(id, tenantId, identifier, mode, status, createdAt, updatedAt,
            null, null, null, null, "openid", null);
    }

    public enum Status { CREATED, IDENTIFIER_VERIFIED, COMPLETED, EXPIRED }

    public ExperienceInteraction complete(Instant now) {
        return new ExperienceInteraction(id, tenantId, identifier, mode, Status.COMPLETED,
            createdAt, now, clientId, redirectUri, codeChallenge, nonce, scope, resource);
    }
}
