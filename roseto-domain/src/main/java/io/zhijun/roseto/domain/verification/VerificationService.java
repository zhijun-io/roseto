package io.zhijun.roseto.domain.verification;

import io.zhijun.roseto.domain.spi.VerificationCodeSender;
import io.zhijun.roseto.domain.spi.VerificationRepository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class VerificationService {
    private final VerificationRepository repository;
    private final VerificationCodeSender sender;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    public VerificationService(VerificationRepository repository, Clock clock) {
        this(repository, null, clock);
    }

    public VerificationService(VerificationRepository repository, VerificationCodeSender sender, Clock clock) {
        this.repository = repository;
        this.sender = sender;
        this.clock = clock;
    }

    public IssueResult issue(UUID tenantId, UUID userId, UUID interactionId, String purpose, String identifier,
                             Duration ttl, int maxAttempts) {
        if (sender == null) throw new IllegalStateException("verification sender is not configured");
        String code = "%06d".formatted(random.nextInt(1_000_000));
        var verification = Verification.issue(UUID.randomUUID(), tenantId, userId, interactionId, purpose, identifier,
            code, Instant.now(clock).plus(ttl), maxAttempts);
        repository.save(verification);
        sender.send(tenantId, identifier, purpose, code);
        return new IssueResult(verification.id(), verification.expiresAt());
    }

    public IssueResult issue(UUID tenantId, UUID userId, String purpose, String identifier,
                             Duration ttl, int maxAttempts) {
        return issue(tenantId, userId, null, purpose, identifier, ttl, maxAttempts);
    }

    public Verification.Result verify(UUID tenantId, UUID verificationId, String code) {
        var current = repository.findByIdAndTenant(tenantId, verificationId)
            .orElseThrow(() -> new IllegalArgumentException("verification not found"));
        var result = current.verify(code, Instant.now(clock));
        repository.save(result.verification());
        return result;
    }

    public record IssueResult(UUID verificationId, Instant expiresAt) {}
}
