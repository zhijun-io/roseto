package io.zhijun.roseto.domain.spi;

import java.util.UUID;

public interface VerificationCodeSender {
    void send(UUID tenantId, String identifier, String purpose, String code);
}
