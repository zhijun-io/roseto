package io.zhijun.roseto.oidc;

import com.nimbusds.jose.jwk.JWKSet;
import io.zhijun.roseto.authorization.AuthorizationController;
import io.zhijun.roseto.domain.spi.ApiResourceRepository;
import io.zhijun.roseto.domain.api.ApiResource;
import io.zhijun.roseto.domain.spi.ApplicationRepository;
import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.spi.UserRepository;
import io.zhijun.roseto.domain.spi.UserSessionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
@RestController
@ConditionalOnProperty(name = "roseto.authorization-server.enabled", havingValue = "false", matchIfMissing = true)
public class OidcController {
    private final ApiResourceRepository apiResources;
    private final ApplicationRepository applications;
    private final PasswordEncoder passwords;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final AuthorizationController authorizations;
    private final JwtEncoder encoder;
    private final SigningKeyService signingKeys;
    private final RefreshTokenService refreshTokens;

    public OidcController(ApiResourceRepository apiResources, ApplicationRepository applications, PasswordEncoder passwords, TenantRepository tenants, UserRepository users,
                          UserSessionRepository sessions, AuthorizationController authorizations,
                          @Qualifier("oidcJwtEncoder") JwtEncoder encoder,
                          @Qualifier("signingKeyService") SigningKeyService signingKeys, RefreshTokenService refreshTokens) {
        this.apiResources = apiResources; this.applications = applications; this.passwords = passwords; this.tenants = tenants; this.users = users;
        this.sessions = sessions; this.authorizations = authorizations; this.encoder = encoder; this.signingKeys = signingKeys; this.refreshTokens = refreshTokens;
    }

    @GetMapping({"/.well-known/openid-configuration", "/.well-known/oauth-authorization-server"})
    public Map<String, Object> discovery() {
        String issuer = tenants.findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found")).issuer().toString();
        String endpointBase = issuer.replaceAll("/+$", "");
        return Map.ofEntries(
            Map.entry("issuer", issuer),
            Map.entry("authorization_endpoint", endpointBase + "/authorize"),
            Map.entry("token_endpoint", endpointBase + "/token"),
            Map.entry("userinfo_endpoint", endpointBase + "/userinfo"),
            Map.entry("jwks_uri", endpointBase + "/jwks"),
            Map.entry("response_types_supported", List.of("code")),
            Map.entry("code_challenge_methods_supported", List.of("S256")),
            Map.entry("subject_types_supported", List.of("public")),
            Map.entry("scopes_supported", List.of("openid", "api:read", "api:write")),
            Map.entry("claims_supported", List.of("iss", "sub", "aud", "iat", "exp", "nonce")),
            Map.entry("id_token_signing_alg_values_supported", List.of("RS256")),
            Map.entry("token_endpoint_auth_methods_supported", List.of("none", "client_secret_basic", "client_secret_post")),
            Map.entry("grant_types_supported", List.of("authorization_code", "client_credentials", "refresh_token"))
        );
    }

    @GetMapping({"/oauth2/jwks", "/jwks"})
    public Map<String, Object> jwks() { return signingKeys.jwks().toJSONObject(); }
    @PostMapping({"/oauth2/token", "/token"})
    public TokenResponse token(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response,
                               @RequestParam String grant_type, @RequestParam(required = false) String code,
                               @RequestParam(required = false) String client_id, @RequestParam(required = false) String client_secret,
                               @RequestParam(required = false) String redirect_uri,
                               @RequestParam(required = false) String code_verifier,
                               @RequestParam(required = false) String refresh_token,
                               @RequestParam(required = false) String scope,
                               @RequestParam(required = false) String resource) {
        response.setHeader("Cache-Control", "no-store");
        if (!"authorization_code".equals(grant_type)
            && !"client_credentials".equals(grant_type)
            && !"refresh_token".equals(grant_type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported grant type");
        }
        var basic = basicCredentials(request);
        if (basic != null) {
            if (client_id != null && !client_id.equals(basic[0])) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "conflicting client authentication");
            if (client_secret != null && !client_secret.equals(basic[1])) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "conflicting client authentication");
            client_id = basic[0]; client_secret = basic[1];
        }
        if (client_id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing client authentication");
        var app = applications.findByClientId(client_id).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid client"));
        if (app.clientSecretHash() != null && (client_secret == null || !passwords.matches(client_secret, app.clientSecretHash()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid client authentication");
        }
        var requestedResource = resource == null ? null : apiResources.findByIndicator(resource)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid resource"));
        if (requestedResource != null && !applications.hasApiResource(app.id(), requestedResource.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resource not allowed");
        }
        if (requestedResource != null && scope != null) {
            for (var requestedScope : scope.trim().split("\\s+")) {
                if (!"openid".equals(requestedScope) && !apiResources.hasScope(requestedResource.id(), requestedScope)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid scope");
                }
            }
        }
        if ("client_credentials".equals(grant_type)) {
            if (app.clientSecretHash() == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "confidential client required");
            }
            var machineScope = scope == null ? "" : scope.trim();
            return issue(client_id, app.tenantId().toString(), client_id, machineScope, null, null, resource, false);
        }
        if ("refresh_token".equals(grant_type)) {
            if (refresh_token == null || refresh_token.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing refresh_token");
            }
            RefreshTokenService.Rotation rotation;
            try {
                rotation = refreshTokens.rotate(refresh_token, client_id, app.tenantId().toString());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            }
            var grant = rotation.grant();
            var user = users.findById(java.util.UUID.fromString(grant.userId()))
                .filter(value -> value.tenantId().equals(grant.tenantId() == null ? null : java.util.UUID.fromString(grant.tenantId())))
                .filter(io.zhijun.roseto.domain.user.User::enabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "user is disabled or unavailable"));
            if (resource != null && !resource.equals(grant.resource())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resource does not match refresh token");
            }
            String effectiveScope = scope == null || scope.isBlank() ? grant.scope() : scope;
            if (!java.util.Set.of(grant.scope().trim().split("\\s+")).containsAll(java.util.Set.of(effectiveScope.trim().split("\\s+")))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid scope");
            }
            return issue(grant.userId(), grant.tenantId(), client_id, effectiveScope,
                rotation.refreshToken(), null, grant.resource(), false);
        }
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing code");
        }
        var authorizationCode = authorizations.exchangeCode(code, code_verifier, client_id, redirect_uri, resource);
        var effectiveResource = authorizationCode.resource();
        var issuedRefreshToken = refreshTokens.issue(new RefreshTokenService.Grant(
            authorizationCode.userId().toString(), client_id, authorizationCode.tenantId().toString(),
            authorizationCode.scope(), effectiveResource));
        return issue(authorizationCode.userId().toString(), authorizationCode.tenantId().toString(), client_id,
            authorizationCode.scope(), issuedRefreshToken, authorizationCode.nonce(), effectiveResource, true);
    }
    private TokenResponse issue(String userId, String tenantId, String clientId, String scope, String refreshToken,
                                String nonce, String resource, boolean includeIdToken) {
        Instant now = Instant.now();
        String issuer = tenants.findById(java.util.UUID.fromString(tenantId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "tenant not found")).issuer().toString();
        var audience = resource == null ? issuer.replaceAll("/+$", "") + "/userinfo" : resource;
        var accessBuilder = JwtClaimsSet.builder().issuer(issuer)
            .subject(userId).audience(List.of(audience)).issuedAt(now).expiresAt(now.plusSeconds(300))
            .claim("tenant_id", tenantId).claim("scope", scope).claim("client_id", clientId)
            .id(java.util.UUID.randomUUID().toString());
        var idBuilder = JwtClaimsSet.builder().issuer(issuer)
            .subject(userId).audience(List.of(clientId)).issuedAt(now).expiresAt(now.plusSeconds(900))
            .claim("tenant_id", tenantId);
        if (nonce != null) {
            idBuilder.claim("nonce", nonce);
        }
        String accessToken = encoder.encode(JwtEncoderParameters.from(accessBuilder.build())).getTokenValue();
        String idToken = includeIdToken
            ? encoder.encode(JwtEncoderParameters.from(idBuilder.build())).getTokenValue() : null;
        return new TokenResponse(accessToken, idToken, refreshToken, 300, "Bearer", scope);
    }
    private String[] basicCredentials(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Basic ")) return null;
        try {
            String value = new String(java.util.Base64.getDecoder().decode(header.substring(6)), java.nio.charset.StandardCharsets.UTF_8);
            int separator = value.indexOf(':');
            if (separator <= 0) throw new IllegalArgumentException();
            return new String[]{value.substring(0, separator), value.substring(separator + 1)};
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid client authentication");
        }
    }

    @PostMapping("/oauth2/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestParam String token) {
        if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing token");
        refreshTokens.revoke(token);
    }
    @RequestMapping(value = "/oauth2/logout", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> logout(@RequestParam(required = false) String refresh_token,
                                       @RequestParam(required = false) String client_id,
                                       @RequestParam(required = false) String post_logout_redirect_uri,
                                       @org.springframework.security.core.annotation.AuthenticationPrincipal
                                       org.springframework.security.oauth2.jwt.Jwt jwt) {
        if (refresh_token != null && !refresh_token.isBlank()) refreshTokens.revoke(refresh_token);
        if (jwt != null) revokeSessions(jwt);
        if (post_logout_redirect_uri == null || post_logout_redirect_uri.isBlank()) {
            return ResponseEntity.noContent().build();
        }
        if (client_id == null || client_id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "client_id is required");
        }
        var app = applications.findByClientId(client_id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid client"));
        if (!applications.hasRedirectUri(app.id(), post_logout_redirect_uri)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid post_logout_redirect_uri");
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(post_logout_redirect_uri)).build();
    }
    private void revokeSessions(org.springframework.security.oauth2.jwt.Jwt jwt) {
        try {
            sessions.revokeByUser(java.util.UUID.fromString(jwt.getClaimAsString("tenant_id")),
                java.util.UUID.fromString(jwt.getSubject()), Instant.now());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid session identity", e);
        }
    }
    public Map<String, Object> userinfo(@org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String expectedAudience = jwt.getIssuer().toString().replaceAll("/+$", "") + "/userinfo";
        if (jwt.getAudience() == null || !jwt.getAudience().contains(expectedAudience)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token audience");
        }
        String scope = jwt.getClaimAsString("scope");
        if (scope == null || !java.util.Set.of(scope.split(" ")).contains("openid")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "openid scope required");
        }
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("sub", jwt.getSubject());
        return result;
    }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record TokenResponse(String access_token, String id_token, String refresh_token, long expires_in, String token_type, String scope) {}

}
