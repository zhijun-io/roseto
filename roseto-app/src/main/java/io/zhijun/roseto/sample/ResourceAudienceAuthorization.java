package io.zhijun.roseto.sample;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("resourceAudience")
public class ResourceAudienceAuthorization {
    private final String indicator;

    public ResourceAudienceAuthorization(
        @Value("${roseto.sample.resource-indicator:https://api.example.com}") String indicator) {
        this.indicator = indicator;
    }

    public boolean has(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken token
            && token.getToken().getAudience() != null
            && token.getToken().getAudience().contains(indicator);
    }
}
