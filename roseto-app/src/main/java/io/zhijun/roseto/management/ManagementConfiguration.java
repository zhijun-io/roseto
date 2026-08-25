package io.zhijun.roseto.management;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;

@Configuration
public class ManagementConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(); }

    @Bean
    JwtDecoder managementJwtDecoder(@Value("${roseto.management.jwt-secret}") String secret) {
        validateJwtSecret(secret);
        var key = new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
        var decoder = NimbusJwtDecoder.withSecretKey(key).build();
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<java.util.List<String>>("aud", values -> values != null && values.contains("roseto-management"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), audience));
        return decoder;
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "roseto.authorization-server.enabled", havingValue = "false", matchIfMissing = true)
    JwtEncoder managementJwtEncoder(@Value("${roseto.management.jwt-secret}") String secret) {
        validateJwtSecret(secret);
        var key = new OctetSequenceKey.Builder(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)).keyID("management")
            .algorithm(com.nimbusds.jose.JWSAlgorithm.HS256).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    private static void validateJwtSecret(String secret) {
        if (secret == null || secret.length() < 32) throw new IllegalStateException("ROSETO_MANAGEMENT_JWT_SECRET must be at least 32 characters");
    }
}
