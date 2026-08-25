package io.zhijun.roseto.authorization;

import io.zhijun.roseto.domain.application.Application;
import io.zhijun.roseto.domain.interaction.ExperienceInteraction;
import io.zhijun.roseto.domain.spi.ApiResourceRepository;
import io.zhijun.roseto.domain.spi.ApplicationRepository;
import io.zhijun.roseto.domain.spi.ExperienceInteractionRepository;
import io.zhijun.roseto.domain.spi.UserRepository;
import io.zhijun.roseto.domain.spi.UserSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import io.zhijun.roseto.domain.session.UserSession;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
@RestController
public class AuthorizationController {
    private final ApiResourceRepository apiResources;
    private final ApplicationRepository applications;
    private final ExperienceInteractionRepository interactions;
    private final UserSessionRepository sessions;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();
    public AuthorizationController(ApiResourceRepository apiResources, ApplicationRepository applications,
                                   ExperienceInteractionRepository interactions, UserSessionRepository sessions,
                                   UserRepository users, PasswordEncoder passwords, StringRedisTemplate redis) {
        this.apiResources = apiResources;
        this.applications = applications;
        this.interactions = interactions;
        this.sessions = sessions;
        this.users = users;
        this.passwords = passwords;
        this.redis = redis;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    private OAuth2AuthorizationService authorizationService;
    private RegisteredClientRepository registeredClients;

    @Autowired(required = false)
    void setAuthorizationServerServices(OAuth2AuthorizationService authorizationService,
                                        RegisteredClientRepository registeredClients) {
        this.authorizationService = authorizationService;
        this.registeredClients = registeredClients;
    }

    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "roseto.authorization-server.enabled", havingValue = "false", matchIfMissing = true)
    @GetMapping({"/oauth2/authorize", "/authorize"})
    public ResponseEntity<String> authorize(@RequestParam String response_type, @RequestParam String client_id,
                                            @RequestParam String redirect_uri, @RequestParam String scope,
                                            @RequestParam(required = false) String resource,
                                            @RequestParam String state, @RequestParam(required = false) String nonce,
                                            @RequestParam String code_challenge, @RequestParam String code_challenge_method) {
        if (scope.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope is required");
        if (!validScope(scope)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "openid scope is required");
        if (state.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "state is required");
        var app = validateRequest(response_type, client_id, redirect_uri, code_challenge, code_challenge_method);
        validateResource(app, resource, scope);
        var now = Instant.now();
        var interaction = interactions.save(new ExperienceInteraction(UUID.randomUUID(), app.tenantId(), "",
            "sign-in", ExperienceInteraction.Status.CREATED, now, now, client_id, redirect_uri,
            code_challenge, nonce, scope == null || scope.isBlank() ? "openid" : scope, resource));
        String html = "<html><body><form method=post action='/oauth2/authorize/login'>"
            + hidden("interaction_id", interaction.id().toString())
            + hidden("client_id", client_id) + hidden("redirect_uri", redirect_uri) + hidden("scope", scope)
            + hidden("resource", resource) + hidden("state", state) + hidden("nonce", nonce)
            + hidden("code_challenge", code_challenge) + hidden("code_challenge_method", code_challenge_method)
            + "<label>Username<input name=username></label><label>Password<input type=password name=password></label>"
            + "<button type=submit>Sign in</button></form></body></html>";
        return ResponseEntity.ok(html);
    }
    @PostMapping({"/oauth2/authorize/login", "/authorize/login"})

    public ResponseEntity<Void> login(@RequestParam String client_id, @RequestParam String redirect_uri,
                                      @RequestParam String scope, @RequestParam(required = false) String resource,
                                      @RequestParam String state,
                                      @RequestParam(required = false) String nonce, @RequestParam String code_challenge, @RequestParam String code_challenge_method,
                                      @RequestParam String username, @RequestParam String password,
                                      @RequestParam(name = "interaction_id", required = false) UUID interactionId) {
        if (scope.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope is required");
        if (state.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "state is required");
        Application app = validateRequest("code", client_id, redirect_uri, code_challenge, code_challenge_method);
        validateResource(app, resource, scope);
        var user = users.findByTenantAndUsername(app.tenantId(), username)
            .filter(value -> value.enabled() && passwords.matches(password, value.passwordHash()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
        ExperienceInteraction interaction = null;
        String grantedScope = scope == null || scope.isBlank() ? "openid" : scope;
        if (interactionId != null) {
            interaction = interactions.findByIdAndTenant(app.tenantId(), interactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "interaction not found"));
            if (interaction.status() == ExperienceInteraction.Status.COMPLETED
                || !client_id.equals(interaction.clientId())
                || !redirect_uri.equals(interaction.redirectUri())
                || !code_challenge.equals(interaction.codeChallenge())
                || !grantedScope.equals(interaction.scope())
                || !sameValue(resource, interaction.resource())
                || !sameValue(nonce, interaction.nonce())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "interaction does not match authorization request");
            }
        }
        if (!validScope(grantedScope)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid scope");
        String code = randomToken();
        var persistedCode = new AuthorizationCode(app.tenantId(), app.id(), user.id(), redirect_uri, grantedScope, resource,
            code_challenge, nonce, Instant.now().plusSeconds(300));
        try {
            redis.opsForValue().set(codeKey(code), objectMapper.writeValueAsString(persistedCode), Duration.ofMinutes(5));
        } catch (Exception e) {
            throw new IllegalStateException("failed to persist authorization code", e);
        }
        if (authorizationService != null && registeredClients != null) {
            var registeredClient = registeredClients.findByClientId(client_id);
            if (registeredClient != null) {
                var authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri("/oauth2/authorize")
                    .clientId(client_id)
                    .redirectUri(redirect_uri)
                    .scopes(java.util.Set.of(grantedScope.trim().split("\\s+")))
                    .state(state)
                    .additionalParameters(parameters -> {
                        parameters.put("code_challenge", code_challenge);
                        parameters.put("code_challenge_method", code_challenge_method);
                        if (resource != null) parameters.put("resource", resource);
                    })
                    .build();
                var authorizationCode = new OAuth2AuthorizationCode(code, Instant.now(), Instant.now().plusSeconds(300));
                authorizationService.save(OAuth2Authorization.withRegisteredClient(registeredClient)
                    .principalName(username)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizedScopes(java.util.Set.of(grantedScope.trim().split("\\s+")))
                    .attribute(OAuth2AuthorizationRequest.class.getName(), authorizationRequest)
                    .attribute("tenant_id", app.tenantId().toString())
                    .attribute("resource", resource)
                    .attribute(java.security.Principal.class.getName(),
                        new UsernamePasswordAuthenticationToken(username, null, java.util.List.of()))
                    .token(authorizationCode)
                    .build());
            }
        }
        if (interaction != null) {
            var now = Instant.now();
            sessions.save(new UserSession(UUID.randomUUID(), app.tenantId(), user.id(), "browser", now, now, null));
            interactions.save(interaction.complete(now));
        }
        var builder = UriComponentsBuilder.fromUriString(redirect_uri).queryParam("code", code);
        if (state != null) builder.queryParam("state", state);
        return ResponseEntity.status(HttpStatus.FOUND).location(builder.build().encode().toUri()).build();
    }
    public String createAuthorizationCode(UUID tenantId, String clientId, UUID userId, String redirectUri,
                                          String scope, String resource, String codeChallenge, String nonce) {
        var app = applications.findByClientId(clientId)
            .filter(value -> value.tenantId().equals(tenantId))
            .filter(value -> applications.hasRedirectUri(value.id(), redirectUri))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid client or redirect_uri"));
        var code = randomToken();
        var persisted = new AuthorizationCode(tenantId, app.id(), userId, redirectUri,
            scope == null || scope.isBlank() ? "openid" : scope, resource, codeChallenge, nonce,
            Instant.now().plusSeconds(300));
        try {
            redis.opsForValue().set(codeKey(code), objectMapper.writeValueAsString(persisted), Duration.ofMinutes(5));
        } catch (Exception e) {
            throw new IllegalStateException("failed to persist authorization code", e);
        }
        return code;
    }
    public AuthorizationCode exchangeCode(String code, String verifier, String clientId, String redirectUri) {
        return exchangeCode(code, verifier, clientId, redirectUri, null);
    }

    public AuthorizationCode exchangeCode(String code, String verifier, String clientId, String redirectUri, String requestedResource) {
        AuthorizationCode stored;
        try {
            var serialized = code == null ? null : redis.opsForValue().getAndDelete(codeKey(code));
            stored = serialized == null ? null : objectMapper.readValue(serialized, AuthorizationCode.class);
        } catch (Exception e) {
            throw new IllegalStateException("failed to load authorization code", e);
        }
        if (stored == null || redirectUri == null || !stored.redirectUri().equals(redirectUri)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid authorization code");
        }
        if (stored.expiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid authorization code");
        }
        if (verifier == null || verifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid code_verifier");
        }
        if (requestedResource != null && !requestedResource.equals(stored.resource())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resource does not match authorization code");
        }
        var app = applications.findById(stored.applicationId())
            .filter(value -> value.clientId().equals(clientId)).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid client"));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            sha256(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        if (!challenge.equals(stored.codeChallenge()) || !applications.hasRedirectUri(app.id(), redirectUri)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid code_verifier");
        }
        return stored;
    }

    private Application validateRequest(String responseType, String clientId, String redirectUri, String challenge, String method) {
        if (!"code".equals(responseType) || challenge.isBlank() || !"S256".equals(method)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PKCE is required");
        }
        return applications.findByClientId(clientId).filter(value -> applications.hasRedirectUri(value.id(), redirectUri))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid client or redirect_uri"));
    }
    private boolean sameValue(String left, String right) {
        return java.util.Objects.equals(left == null || left.isBlank() ? null : left,
            right == null || right.isBlank() ? null : right);
    }

    private boolean validScope(String scope) {
        if (scope == null || scope.isBlank()) return false;
        return java.util.Set.of(scope.trim().split("\\s+")).contains("openid");
    }

    private void validateResource(Application app, String resource, String scope) {
        if (scope == null || scope.isBlank() || scope.trim().equals("openid")) return;
        if (resource == null || resource.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resource is required");
        }
        var apiResource = apiResources.findByIndicator(resource)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid resource"));
        if (!applications.hasApiResource(app.id(), apiResource.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resource not allowed");
        }
        for (var requestedScope : scope.trim().split("\\s+")) {
            if (!"openid".equals(requestedScope) && !apiResources.hasScope(apiResource.id(), requestedScope)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid scope");
            }
        }
    }


    private byte[] sha256(byte[] input) { try { return java.security.MessageDigest.getInstance("SHA-256").digest(input); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String hidden(String name, String value) {
        String escaped = value == null ? "" : value.replace("&", "&amp;").replace("'", "&#39;").replace("<", "&lt;").replace(">", "&gt;");
        return "<input type=hidden name='" + name + "' value='" + escaped + "'>";
    }
    private String randomToken() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String codeKey(String code) {
        return "roseto:authorization-code:" + code;
    }

    public record AuthorizationCode(UUID tenantId, UUID applicationId, UUID userId, String redirectUri, String scope, String resource, String codeChallenge, String nonce, Instant expiresAt) {}
}
