package io.zhijun.roseto.domain.api;

import java.time.Instant;
import java.util.UUID;

public record ApiResource(UUID id, UUID tenantId, String indicator, String name, Instant createdAt) {}
