package io.zhijun.roseto.domain.application;

import java.time.Instant;
import java.util.UUID;

public record Application(UUID id, UUID tenantId, String clientId, String clientSecretHash,
                          String name, Instant createdAt) {
}
