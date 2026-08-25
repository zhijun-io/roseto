package io.zhijun.roseto.domain.management;

import java.time.Instant;
import java.util.UUID;

public record ManagementAccount(UUID id, String username, String passwordHash, Instant createdAt) {
}
