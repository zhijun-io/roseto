package io.zhijun.roseto.security;

import io.zhijun.roseto.domain.application.Application;
import io.zhijun.roseto.domain.spi.ApplicationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class RosetoRegisteredClientRepository implements RegisteredClientRepository {
    private final ApplicationRepository applications;

    public RosetoRegisteredClientRepository(ApplicationRepository applications) {
        this.applications = applications;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("Application registration is owned by Roseto Management API");
    }

    @Override
    public RegisteredClient findById(String id) {
        try {
            return applications.findById(UUID.fromString(id)).map(this::toRegisteredClient).orElse(null);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return applications.findByClientId(clientId).map(this::toRegisteredClient).orElse(null);
    }

    private RegisteredClient toRegisteredClient(Application application) {
        var builder = RegisteredClient.withId(application.id().toString())
            .clientId(application.clientId())
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .scope("openid")
            .scope("api:read")
            .scope("api:write")
            .clientSettings(ClientSettings.builder().requireProofKey(true).build());
        if (application.clientSecretHash() != null) {
            builder.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);
        }
        if (application.clientSecretHash() == null) {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .clientSecret(application.clientSecretHash());
        }
        applications.redirectUris(application.id()).forEach(builder::redirectUri);
        return builder.build();
    }
}
