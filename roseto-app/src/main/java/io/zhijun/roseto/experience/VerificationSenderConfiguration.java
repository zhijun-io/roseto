package io.zhijun.roseto.experience;

import io.zhijun.roseto.domain.spi.VerificationCodeSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Configuration
public class VerificationSenderConfiguration {
    @Bean
    VerificationDeliveryLog verificationDeliveryLog() { return new VerificationDeliveryLog(); }

    @Bean
    VerificationCodeSender verificationCodeSender(VerificationDeliveryLog log) {
        return log::record;
    }

    public static final class VerificationDeliveryLog {
        private final ConcurrentMap<UUID, String> codes = new ConcurrentHashMap<>();
        void record(UUID tenantId, String identifier, String purpose, String code) {
            codes.put(tenantId, purpose + ":" + identifier + ":" + code);
        }
        public String last(UUID tenantId) { return codes.get(tenantId); }
    }
}
