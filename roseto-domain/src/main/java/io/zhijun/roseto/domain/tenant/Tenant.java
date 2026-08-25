package io.zhijun.roseto.domain.tenant;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record Tenant(UUID id, String publicKey, String name, URI issuer, Instant createdAt) {
}
