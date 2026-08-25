package io.zhijun.roseto.oidc;

import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@Configuration
public class OidcConfiguration {
    @Bean
    @Primary
    SigningKeyService signingKeyService() throws Exception { return new SigningKeyService(); }

    @Bean("oidcJwtEncoder")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "roseto.authorization-server.enabled", havingValue = "false", matchIfMissing = true)
    SigningKeyService oidcJwtEncoder(SigningKeyService service) { return service; }

    @Bean
    RefreshTokenService refreshTokenService(io.zhijun.roseto.domain.spi.RefreshTokenRepository repository) {
        return new RefreshTokenService(repository);
    }
    @Bean
    @Primary
    JwtDecoder rosetoJwtDecoder(@Qualifier("signingKeyService") SigningKeyService signingKeys,
                                @Qualifier("managementJwtDecoder") JwtDecoder management,
                                io.zhijun.roseto.domain.spi.TenantRepository tenants) {
        return token -> {
            try {
                var algorithm = com.nimbusds.jwt.SignedJWT.parse(token).getHeader().getAlgorithm().getName();
                if (algorithm.startsWith("HS")) return management.decode(token);
                JwtException failure = null;
                for (RSAKey key : signingKeys.verificationKeys()) {
                    try {
                        var jwt = org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build().decode(token);
                        String tenantId = jwt.getClaimAsString("tenant_id");
                        var tenant = tenantId == null ? java.util.Optional.<io.zhijun.roseto.domain.tenant.Tenant>empty()
                            : tenants.findById(java.util.UUID.fromString(tenantId));
                        if (tenant.isEmpty() || !tenant.get().issuer().normalize().equals(java.net.URI.create(jwt.getIssuer().toString()).normalize())
                            || jwt.getAudience() == null || jwt.getAudience().isEmpty())
                            throw new JwtException("Invalid OIDC claims");
                        return jwt;
                    } catch (JwtException | IllegalArgumentException e) { failure = e instanceof JwtException j ? j : new JwtException("Invalid OIDC claims", e); }
                }
                throw failure == null ? new JwtException("Invalid JWT") : failure;
            } catch (java.text.ParseException | com.nimbusds.jose.JOSEException e) {
                throw new JwtException("Invalid JWT", e);
            }
        };
    }
}
