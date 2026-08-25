package io.zhijun.roseto.management;

import io.zhijun.roseto.domain.management.ManagementAccount;
import io.zhijun.roseto.domain.spi.ManagementAccountRepository;
import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.tenant.Tenant;
import io.zhijun.roseto.domain.spi.ApplicationRepository;
import io.zhijun.roseto.domain.application.Application;
import io.zhijun.roseto.domain.api.ApiResource;
import io.zhijun.roseto.domain.spi.ApiResourceRepository;
import io.zhijun.roseto.domain.api.Role;
import io.zhijun.roseto.domain.spi.PermissionRepository;
import io.zhijun.roseto.oidc.SigningKeyService;
import io.zhijun.roseto.domain.spi.RoleRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import io.zhijun.roseto.domain.user.User;
import io.zhijun.roseto.domain.spi.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "roseto.authorization-server.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/management")
public class ManagementController {
    private final TenantRepository tenants;
    private final ManagementAccountRepository accounts;
    private final ApiResourceRepository apiResources;
    private final ApplicationRepository applications;
    private final UserRepository users;
    private final PermissionRepository permissions;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final SigningKeyService signingKeys;
    private final JwtEncoder jwtEncoder;
    private final String bootstrapSecret;
    public ManagementController(TenantRepository tenants, ManagementAccountRepository accounts,
                                ApplicationRepository applications, UserRepository users, ApiResourceRepository apiResources,
                                PermissionRepository permissions, RoleRepository roles,
                                PasswordEncoder passwords, @Qualifier("signingKeyService") SigningKeyService signingKeys,
                                @Qualifier("managementJwtEncoder") JwtEncoder jwtEncoder,
                                @Value("${roseto.management.bootstrap-secret}") String bootstrapSecret) {
        this.tenants = tenants;
        this.accounts = accounts;
        this.apiResources = apiResources;
        this.applications = applications;
        this.users = users;
        this.permissions = permissions;
        this.roles = roles;
        this.passwords = passwords;
        this.signingKeys = signingKeys;
        this.jwtEncoder = jwtEncoder;
        if (bootstrapSecret == null || bootstrapSecret.isBlank()) throw new IllegalStateException("ROSETO_MANAGEMENT_BOOTSTRAP_SECRET must not be blank");
        this.bootstrapSecret = bootstrapSecret;
    }

    @PostMapping("/bootstrap")
    @ResponseStatus(HttpStatus.CREATED)
    public BootstrapResponse bootstrap(@RequestHeader("X-Roseto-Bootstrap-Secret") String suppliedSecret,
                                       @RequestBody BootstrapRequest request) {
        if (!constantTimeEquals(bootstrapSecret, suppliedSecret) || tenants.count() != 0 || accounts.count() != 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "bootstrap unavailable");
        }
        if (request == null || isBlank(request.tenantName()) || isBlank(request.publicKey())
            || isBlank(request.issuer()) || isBlank(request.username()) || isBlank(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid bootstrap request");
        }
        URI issuer;
        try {
            issuer = URI.create(request.issuer());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid issuer");
        }
        if (!issuer.isAbsolute() || issuer.getQuery() != null || issuer.getFragment() != null
            || (!"https".equalsIgnoreCase(issuer.getScheme()) && !"http".equalsIgnoreCase(issuer.getScheme()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid issuer");
        }
        var now = Instant.now();
        var tenant = new Tenant(UUID.randomUUID(), request.publicKey(), request.tenantName(), issuer, now);
        tenants.save(tenant);
        accounts.save(new ManagementAccount(UUID.randomUUID(), request.username(), passwords.encode(request.password()), now));
        return new BootstrapResponse(tenant.id(), tenant.publicKey());
    }
    @PostMapping("/signing-keys/rotate")
    public SigningKeyRotationResponse rotateSigningKey(Authentication authentication) {
        requireManagement(authentication);
        try {
            signingKeys.rotate();
            return new SigningKeyRotationResponse(signingKeys.current().getKeyID());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "unable to rotate signing key", e);
        }
    }
    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse createApplication(Authentication authentication, @RequestBody ApplicationRequest request) {
        return createApplicationForTenant(authentication, singleTenantId(), request, false);
    }

    ApplicationResponse createApplicationForTenant(Authentication authentication, UUID tenantId,
                                                   ApplicationRequest request, boolean requireResource) {
        requireManagement(authentication);
        if (request == null || isBlank(request.name())
            || request.redirectUris() == null || request.redirectUris().isEmpty()
            || request.redirectUris().stream().anyMatch(uri -> !isHttpUri(uri))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid application request");
        }
        var tenant = tenants.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found"));
        io.zhijun.roseto.domain.api.ApiResource resource = isBlank(request.resourceIndicator()) ? null : apiResources.findByIndicator(request.resourceIndicator())
            .filter(value -> value.tenantId().equals(tenantId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "api resource not found"));
        if (requireResource && resource == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceIndicator is required");
        }
        var clientId = isBlank(request.clientId()) ? "roseto-" + UUID.randomUUID() : request.clientId();
        if (applications.findByClientId(clientId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "client_id already registered");
        }
        var application = new Application(UUID.randomUUID(), tenant.id(), clientId,
            request.clientSecret() == null ? null : passwords.encode(request.clientSecret()), request.name(), Instant.now());
        applications.save(application);
        request.redirectUris().forEach(uri -> applications.addRedirectUri(application.id(), uri));
        if (resource != null) applications.addApiResource(application.id(), resource.id());
        return new ApplicationResponse(application.id(), application.clientId());
    }

    @PostMapping("/api-resources")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResourceResponse createApiResource(Authentication authentication, @RequestBody ApiResourceRequest request) {
        return createApiResourceForTenant(authentication, singleTenantId(), request);
    }

    ApiResourceResponse createApiResourceForTenant(Authentication authentication, UUID tenantId,
                                                   ApiResourceRequest request) {
        requireManagement(authentication);
        if (request == null || isBlank(request.indicator()) || !isHttpUri(request.indicator()) || isBlank(request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid api resource request");
        }
        var tenant = tenants.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found"));
        if (apiResources.findByIndicator(request.indicator()).filter(value -> value.tenantId().equals(tenant.id())).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "api resource already registered");
        }
        var resource = new ApiResource(UUID.randomUUID(), tenant.id(), request.indicator(), request.name(), Instant.now());
        apiResources.save(resource);
        return new ApiResourceResponse(resource.id(), resource.indicator());
    }
    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse createRole(Authentication authentication, @RequestBody RoleRequest request) {
        requireManagement(authentication);
        if (request == null || isBlank(request.name()) || !request.name().matches("[A-Za-z0-9:_-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid role request");
        }
        var tenant = tenants.findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found"));
        if (roles.findByTenantAndName(tenant.id(), request.name()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "role already registered");
        }
        var role = new Role(UUID.randomUUID(), tenant.id(), request.name(), request.description(), Instant.now());
        roles.save(role);
        return new RoleResponse(role.id(), role.name());
    }
    @PostMapping("/roles/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    public RolePermissionResponse addRolePermission(Authentication authentication,
                                                     @RequestBody RolePermissionRequest request) {
        requireManagement(authentication);
        if (request == null || isBlank(request.roleName()) || isBlank(request.resourceIndicator())
            || isBlank(request.permissionName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid role permission request");
        }
        var tenant = tenants.findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found"));
        var role = roles.findByTenantAndName(tenant.id(), request.roleName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "role not found"));
        var resource = apiResources.findByIndicator(request.resourceIndicator())
            .filter(value -> value.tenantId().equals(tenant.id()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "api resource not found"));
        var permission = permissions.findByTenantResourceAndName(tenant.id(), resource.id(), request.permissionName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "permission not found"));
        if (roles.hasPermission(role.id(), permission.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "permission already assigned");
        }
        roles.addPermission(role.id(), permission.id());
        return new RolePermissionResponse(role.name(), permission.name());
    }

    PermissionResponse createPermissionForTenant(Authentication authentication, UUID tenantId,
                                                 PermissionRequest request) {
        requireManagement(authentication);
        if (request == null || isBlank(request.resourceIndicator()) || isBlank(request.name())
            || !request.name().matches("[A-Za-z0-9:_-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid permission request");
        }
        var tenant = tenants.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found"));
        var resource = apiResources.findByIndicator(request.resourceIndicator())
            .filter(value -> value.tenantId().equals(tenant.id()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "api resource not found"));
        if (permissions.findByTenantResourceAndName(tenant.id(), resource.id(), request.name()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "permission already registered");
        }
        var permission = new io.zhijun.roseto.domain.api.Permission(
            UUID.randomUUID(), tenant.id(), resource.id(), request.name(), request.description(), Instant.now());
        permissions.save(permission);
        return new PermissionResponse(permission.id(), resource.indicator(), permission.name());
    }

    @PostMapping("/api-resources/scopes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiScopeResponse createApiScope(Authentication authentication, @RequestBody ApiScopeRequest request) {
        requireManagement(authentication);
        if (request == null || isBlank(request.indicator()) || isBlank(request.scope())
            || !request.scope().matches("[A-Za-z0-9:_-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid api scope request");
        }
        var resource = apiResources.findByIndicator(request.indicator())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "api resource not found"));
        if (apiResources.hasScope(resource.id(), request.scope())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "api scope already registered");
        }
        apiResources.addScope(resource.id(), request.scope());
        return new ApiScopeResponse(resource.indicator(), request.scope());
    }
    @GetMapping("/api-resources/scopes")
    public ApiScopesResponse listApiScopes(Authentication authentication, @RequestParam String indicator) {
        requireManagement(authentication);
        var resource = apiResources.findByIndicator(indicator)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "api resource not found"));
        return new ApiScopesResponse(resource.indicator(), apiResources.scopes(resource.id()));
    }
    @DeleteMapping("/api-resources/scopes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteApiScope(Authentication authentication, @RequestParam String indicator,
                                @RequestParam String scope) {
        requireManagement(authentication);
        var resource = apiResources.findByIndicator(indicator)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "api resource not found"));
        if (!apiResources.hasScope(resource.id(), scope)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "api scope not found");
        }
        apiResources.removeScope(resource.id(), scope);
    }

    @PostMapping("/applications/api-resources")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResourceResponse attachApiResource(Authentication authentication,
                                                         @RequestBody ApplicationResourceRequest request) {
        requireManagement(authentication);
        if (request == null || isBlank(request.clientId()) || isBlank(request.indicator())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid application resource request");
        }
        var application = applications.findByClientId(request.clientId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "application not found"));
        var resource = apiResources.findByIndicator(request.indicator())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "api resource not found"));
        if (!application.tenantId().equals(resource.tenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "api resource not found");
        }
        if (applications.hasApiResource(application.id(), resource.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "api resource already attached");
        }
        applications.addApiResource(application.id(), resource.id());
        return new ApplicationResourceResponse(application.clientId(), resource.indicator());
    }

    @PostMapping({"/users", "/tenants/{tenantId}/users"})
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(Authentication authentication,
                                   @PathVariable(required = false) UUID tenantId,
                                   @RequestBody UserRequest request) {
        requireManagement(authentication);
        if (request == null || isBlank(request.password())
            || (isBlank(request.username()) && isBlank(request.email()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid user request");
        }
        var resolvedTenantId = tenantId == null ? singleTenantId() : tenantId;
        if (tenants.findById(resolvedTenantId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found");
        }
        var username = isBlank(request.username()) ? request.email() : request.username();
        if (users.findByTenantAndUsername(resolvedTenantId, username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username already registered");
        }
        var user = new User(UUID.randomUUID(), resolvedTenantId, username,
            passwords.encode(request.password()), true, Instant.now());
        try {
            users.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username already registered", e);
        }
        return new UserResponse(user.id(), user.username());
    }
    @PostMapping("/users/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public UserRoleResponse assignRole(Authentication authentication, @RequestBody UserRoleRequest request) {
        requireManagement(authentication);
        if (request == null || isBlank(request.username()) || isBlank(request.roleName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid user role request");
        }
        var tenantId = singleTenantId();
        var user = users.findByTenantAndUsername(tenantId, request.username())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        var role = roles.findByTenantAndName(tenantId, request.roleName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "role not found"));
        if (users.hasRole(user.id(), role.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "role already assigned");
        }
        users.addRole(user.id(), role.id());
        return new UserRoleResponse(user.username(), role.name());
    }
    @DeleteMapping("/users/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRole(Authentication authentication, @RequestBody UserRoleRequest request) {
        requireManagement(authentication);
        if (request == null || isBlank(request.username()) || isBlank(request.roleName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid user role request");
        }
        var tenantId = singleTenantId();
        var user = users.findByTenantAndUsername(tenantId, request.username())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        var role = roles.findByTenantAndName(tenantId, request.roleName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "role not found"));
        if (!users.hasRole(user.id(), role.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "role assignment not found");
        }
        users.removeRole(user.id(), role.id());
    }


    private void requireManagement(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)
            || !jwt.getAudience().contains("roseto-management")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "management token required");
        }
    }

    private UUID singleTenantId() {
        return tenants.findFirst().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found")).id();
    }

    @PostMapping("/token")
    public TokenResponse token(@RequestBody TokenRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid token request");
        }
        var account = accounts.findByUsername(request.username())
            .filter(value -> passwords.matches(request.password(), value.passwordHash()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
            .issuer("roseto")
            .subject(account.id().toString())
            .audience(java.util.List.of("roseto-management"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .build();
        var headers = org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        return new TokenResponse(jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue(), 900);
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public record BootstrapRequest(String tenantName, String publicKey, String issuer,
                                   String username, String password) {}

    public record ApiScopeRequest(String indicator, String scope) {}
    public record ApiScopeResponse(String indicator, String scope) {}
    public record ApplicationResourceRequest(String clientId, String indicator) {}
    public record ApplicationResourceResponse(String clientId, String indicator) {}
    public record BootstrapResponse(UUID tenantId, String publicKey) {}

    private boolean isHttpUri(String value) {
        if (isBlank(value)) return false;
        try {
            var uri = URI.create(value);
            return uri.isAbsolute() && uri.getFragment() == null
                && ("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    public record TokenRequest(String username, String password) {}
    public record TokenResponse(String accessToken, long expiresIn) {}
    public record ApiResourceRequest(String indicator, String name) {}
    public record ApiResourceResponse(UUID resourceId, String indicator) {}
    public record ApiScopesResponse(String indicator, java.util.List<String> scopes) {}
    public record ApplicationRequest(String name, String clientId, String clientSecret, String type,
                                     java.util.List<String> redirectUris, String resourceIndicator) {}
    public record SigningKeyRotationResponse(String keyId) {}
    public record ApplicationResponse(UUID applicationId, String clientId) {}
    public record UserRequest(String username, String email, String password) {}
    public record PermissionRequest(String resourceIndicator, String name, String description) {}
    public record PermissionResponse(UUID permissionId, String resourceIndicator, String name) {}
    public record UserResponse(UUID userId, String username) {}
    public record RoleRequest(String name, String description) {}
    public record RoleResponse(UUID roleId, String name) {}
    public record RolePermissionRequest(String roleName, String resourceIndicator, String permissionName) {}
    public record RolePermissionResponse(String roleName, String permissionName) {}
    public record UserRoleRequest(String username, String roleName) {}
    public record UserRoleResponse(String username, String roleName) {}
}
