package io.zhijun.roseto.domain.api;

import java.time.Instant;
import java.util.UUID;

public record Permission(UUID id, UUID tenantId, UUID resourceId, String name, String description,
                         Instant createdAt) {}
