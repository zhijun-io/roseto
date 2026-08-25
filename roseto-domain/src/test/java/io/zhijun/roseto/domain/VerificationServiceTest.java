package io.zhijun.roseto.domain;

import io.zhijun.roseto.domain.spi.VerificationCodeSender;
import io.zhijun.roseto.domain.spi.VerificationRepository;
import io.zhijun.roseto.domain.verification.Verification;
import io.zhijun.roseto.domain.verification.VerificationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VerificationServiceTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void codeIsSentHashedAndConsumedOnce() {
        var store = new Store();
        var sender = new Sender();
        var service = new VerificationService(store, sender, Clock.fixed(now, ZoneOffset.UTC));
        var tenant = UUID.randomUUID();
        var issued = service.issue(tenant, UUID.randomUUID(), "email_verification", "a@example.com", Duration.ofMinutes(5), 3);

        assertNotNull(issued.verificationId());
        var accepted = service.verify(tenant, issued.verificationId(), sender.code);
        assertTrue(accepted.accepted());
        assertEquals(Verification.Status.CONSUMED, accepted.verification().status());
        assertFalse(service.verify(tenant, issued.verificationId(), sender.code).accepted());
    }

    @Test
    void wrongCodesLockAfterMaximumAttempts() {
        var store = new Store();
        var sender = new Sender();
        var service = new VerificationService(store, sender, Clock.fixed(now, ZoneOffset.UTC));
        var tenant = UUID.randomUUID();
        var issued = service.issue(tenant, UUID.randomUUID(), "password_reset", "a@example.com", Duration.ofMinutes(5), 2);

        assertFalse(service.verify(tenant, issued.verificationId(), "000000").accepted());
        var locked = service.verify(tenant, issued.verificationId(), "000000");
        assertEquals("invalid_code", locked.error());
        assertEquals(Verification.Status.LOCKED, locked.verification().status());
        assertFalse(service.verify(tenant, issued.verificationId(), sender.code).accepted());
    }

    @Test
    void tenantIsRequiredForLookup() {
        var store = new Store();
        var sender = new Sender();
        var service = new VerificationService(store, sender, Clock.fixed(now, ZoneOffset.UTC));
        var issued = service.issue(UUID.randomUUID(), UUID.randomUUID(), "email_verification", "a@example.com", Duration.ofMinutes(5), 3);
        assertThrows(IllegalArgumentException.class,
            () -> service.verify(UUID.randomUUID(), issued.verificationId(), sender.code));
    }

    private static final class Store implements VerificationRepository {
        private final Map<UUID, Verification> values = new HashMap<>();
        public Verification save(Verification verification) { values.put(verification.id(), verification); return verification; }
        public Optional<Verification> findByIdAndTenant(UUID tenantId, UUID verificationId) {
            return Optional.ofNullable(values.get(verificationId)).filter(value -> value.tenantId().equals(tenantId));
        }
    }

    private static final class Sender implements VerificationCodeSender {
        private String code;
        public void send(UUID tenantId, String identifier, String purpose, String code) { this.code = code; }
    }
}
