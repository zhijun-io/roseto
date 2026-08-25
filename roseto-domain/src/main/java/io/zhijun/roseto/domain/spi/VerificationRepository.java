package io.zhijun.roseto.domain.spi;

import io.zhijun.roseto.domain.verification.Verification;

import java.util.Optional;
import java.util.UUID;

public interface VerificationRepository {
    Verification save(Verification verification);
    Optional<Verification> findByIdAndTenant(UUID tenantId, UUID verificationId);
}
