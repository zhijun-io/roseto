package io.zhijun.roseto.security;

import org.springframework.beans.factory.annotation.Qualifier;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.zhijun.roseto.oidc.SigningKeyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.spi.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
@ConditionalOnProperty(name = "roseto.authorization-server.enabled", havingValue = "true")
public class AuthorizationServerBeansConfiguration {
    @Bean
    JWKSource<SecurityContext> authorizationServerJwkSource(@Qualifier("signingKeyService") SigningKeyService signingKeys) {
        return (selector, context) -> selector.select(signingKeys.jwks());
    }

    @Bean
    @org.springframework.context.annotation.Primary
    org.springframework.security.oauth2.jwt.JwtEncoder authorizationServerJwtEncoder(
        @Qualifier("signingKeyService") SigningKeyService signingKeys) {
        return signingKeys;
    }


    @Bean
    org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator<?> authorizationServerTokenGenerator(
        @Qualifier("signingKeyService") SigningKeyService signingKeys,
        io.zhijun.roseto.domain.spi.TenantRepository tenants,
        io.zhijun.roseto.domain.spi.UserRepository users) {
        var jwt = new org.springframework.security.oauth2.server.authorization.token.JwtGenerator(signingKeys);
        jwt.setJwtCustomizer(context -> {
            var authorization = context.getAuthorization();
            var tenantId = authorization == null ? null : authorization.getAttribute("tenant_id");
            if (tenantId == null && context.getPrincipal() != null) {
                tenantId = tenants.findFirst()
                    .filter(tenant -> users.findByTenantAndUsername(tenant.id(),
                        context.getPrincipal().getName()).isPresent())
                    .map(tenant -> tenant.id().toString())
                    .orElse(null);
            }
            if (tenantId != null) {
                tenants.findById(java.util.UUID.fromString((String) tenantId))
                    .ifPresent(tenant -> context.getClaims().issuer(tenant.issuer().toString()));
                context.getClaims().claim("tenant_id", tenantId);
            }
            var resource = authorization == null ? null : (String) authorization.getAttribute("resource");
            if (context.getTokenType().equals(org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN)) {
                context.getClaims().claim("client_id", context.getRegisteredClient().getClientId());
                if (resource != null) {
                    context.getClaims().audience(java.util.List.of(resource));
                } else if (tenantId != null) {
                    var tenant = tenants.findById(java.util.UUID.fromString((String) tenantId)).orElse(null);
                    if (tenant != null) context.getClaims().audience(java.util.List.of(
                        tenant.issuer().toString().replaceAll("/+$", "") + "/userinfo"));
                }
            }
        });
        return new org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator(
            jwt,
            new org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator());
    }
    @Bean
    AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    @Bean
    UserDetailsService authorizationServerUserDetailsService(TenantRepository tenants, UserRepository users) {
        return username -> tenants.findFirst()
            .flatMap(tenant -> users.findByTenantAndUsername(tenant.id(), username))
            .filter(user -> user.enabled())
            .map(user -> org.springframework.security.core.userdetails.User.withUsername(user.username())
                .password(user.passwordHash()).authorities("ROLE_USER").build())
            .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(username));
    }


    @Bean
    @org.springframework.context.annotation.Profile("test")
    OAuth2AuthorizationService testAuthorizationService() {
        return new org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService();
    }
    @Bean
    @org.springframework.context.annotation.Profile("!test")
    OAuth2AuthorizationService authorizationService(JdbcOperations jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(JdbcOperations jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }

    @Bean
    JwtDecoder authorizationServerJwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
}
