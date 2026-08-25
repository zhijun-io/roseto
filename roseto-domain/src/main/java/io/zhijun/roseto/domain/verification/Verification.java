package io.zhijun.roseto.domain.verification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

public record Verification(UUID id, UUID tenantId, UUID userId, String purpose, String identifier,
                           String codeHash, Instant expiresAt, int attempts, int maxAttempts, Status status,
                           UUID interactionId) {
    public Verification(UUID id, UUID tenantId, UUID userId, String purpose, String identifier,
                        String codeHash, Instant expiresAt, int attempts, int maxAttempts, Status status) {
        this(id, tenantId, userId, purpose, identifier, codeHash, expiresAt, attempts, maxAttempts, status, null);
    }

    public enum Status { PENDING, CONSUMED, EXPIRED, LOCKED }

    public static Verification issue(UUID id, UUID tenantId, UUID userId, String purpose,
                                     String identifier, String code, Instant expiresAt, int maxAttempts) {
        return issue(id, tenantId, userId, null, purpose, identifier, code, expiresAt, maxAttempts);
    }

    public static Verification issue(UUID id, UUID tenantId, UUID userId, UUID interactionId,
                                     String purpose, String identifier, String code,
                                     Instant expiresAt, int maxAttempts) {
        if (maxAttempts < 1 || expiresAt == null) {
            throw new IllegalArgumentException("invalid verification policy");
        }
        return new Verification(id, tenantId, userId, purpose, identifier, hash(code), expiresAt,
            0, maxAttempts, Status.PENDING, interactionId);
    }

    public Result verify(String code, Instant now) {
        if (status != Status.PENDING) return new Result(this, false, "not_pending");
        if (!expiresAt.isAfter(now)) return new Result(withStatus(Status.EXPIRED), false, "expired");
        if (attempts >= maxAttempts) return new Result(withStatus(Status.LOCKED), false, "too_many_attempts");
        if (!MessageDigest.isEqual(codeHash.getBytes(StandardCharsets.UTF_8), hash(code).getBytes(StandardCharsets.UTF_8))) {
            int nextAttempts = attempts + 1;
            return nextAttempts >= maxAttempts
                ? new Result(new Verification(id, tenantId, userId, purpose, identifier, codeHash, expiresAt,
                    nextAttempts, maxAttempts, Status.LOCKED, interactionId), false, "invalid_code")
                : new Result(new Verification(id, tenantId, userId, purpose, identifier, codeHash, expiresAt,
                    nextAttempts, maxAttempts, Status.PENDING, interactionId), false, "invalid_code");
        }
        return new Result(withStatus(Status.CONSUMED), true, null);
    }

    private Verification withStatus(Status next) {
        return new Verification(id, tenantId, userId, purpose, identifier, codeHash, expiresAt,
            attempts, maxAttempts, next, interactionId);
    }

    private static String hash(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("verification code required");
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Result(Verification verification, boolean accepted, String error) {}
}
