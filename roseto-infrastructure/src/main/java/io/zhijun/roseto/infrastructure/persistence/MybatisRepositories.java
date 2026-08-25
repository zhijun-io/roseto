package io.zhijun.roseto.infrastructure.persistence;

import io.zhijun.roseto.domain.api.ApiResource;
import io.zhijun.roseto.domain.api.Permission;
import io.zhijun.roseto.domain.application.Application;
import io.zhijun.roseto.domain.management.ManagementAccount;
import io.zhijun.roseto.domain.verification.Verification;
import io.zhijun.roseto.domain.session.UserSession;
import io.zhijun.roseto.domain.spi.*;
import io.zhijun.roseto.domain.tenant.Tenant;
import io.zhijun.roseto.domain.user.User;
import org.springframework.stereotype.Repository;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import static io.zhijun.roseto.infrastructure.persistence.PersistenceEntities.*;

final class PersistenceMapping {
    static Tenant tenant(TenantDO x) { return new Tenant(x.id, x.publicKey, x.name, URI.create(x.issuer), x.createdAt); }
    static ManagementAccount account(ManagementAccountDO x) { return new ManagementAccount(x.id, x.username, x.passwordHash, x.createdAt); }
    static Application application(ApplicationDO x) { return new Application(x.id, x.tenantId, x.clientId, x.clientSecretHash, x.name, x.createdAt); }
    static ApiResource apiResource(ApiResourceDO x) { return new ApiResource(x.id, x.tenantId, x.indicator, x.name, x.createdAt); }
    static Verification verification(VerificationDO x) {
        return new Verification(x.id, x.tenantId, x.userId, x.purpose, x.identifier, x.codeHash,
            x.expiresAt, x.attempts, x.maxAttempts, Verification.Status.valueOf(x.status), x.interactionId);
    }
    static UserSession userSession(UserSessionDO x) {
        return new UserSession(x.id, x.tenantId, x.userId, x.device, x.createdAt, x.lastSeenAt, x.revokedAt);
    }
    static Permission permission(PermissionDO x) { return new Permission(x.id, x.tenantId, x.resourceId, x.name, x.description, x.createdAt); }
    static io.zhijun.roseto.domain.api.Role role(RoleDO x) {
        return new io.zhijun.roseto.domain.api.Role(x.id, x.tenantId, x.name, x.description, x.createdAt);
    }
    static User user(UserDO x) { return new User(x.id, x.tenantId, x.username, x.passwordHash, x.enabled, x.createdAt); }
    static io.zhijun.roseto.domain.interaction.ExperienceInteraction interaction(ExperienceInteractionDO x) {
        return new io.zhijun.roseto.domain.interaction.ExperienceInteraction(x.id, x.tenantId, x.identifier,
            x.mode, io.zhijun.roseto.domain.interaction.ExperienceInteraction.Status.valueOf(x.status),
            x.createdAt, x.updatedAt, x.clientId, x.redirectUri, x.codeChallenge, x.nonce, x.scope, x.resource);
    }
}
@Repository
class MybatisTenantRepository implements TenantRepository {
    private final TenantMapper mapper; MybatisTenantRepository(TenantMapper mapper) { this.mapper = mapper; }
    public Tenant save(Tenant x) { var d = new TenantDO(); d.id=x.id(); d.publicKey=x.publicKey(); d.name=x.name(); d.issuer=x.issuer().toString(); d.createdAt=x.createdAt(); mapper.insert(d); return x; }
    public Optional<Tenant> findById(UUID id) { return Optional.ofNullable(mapper.byId(id)).map(PersistenceMapping::tenant); }
    public Optional<Tenant> findByPublicKey(String key) { return Optional.ofNullable(mapper.byKey(key)).map(PersistenceMapping::tenant); }
    public Optional<Tenant> findFirst() { return Optional.ofNullable(mapper.first()).map(PersistenceMapping::tenant); }
    public long count() { return mapper.countRows(); }
}
@Repository
class MybatisManagementAccountRepository implements ManagementAccountRepository {
    private final ManagementAccountMapper mapper; MybatisManagementAccountRepository(ManagementAccountMapper mapper) { this.mapper=mapper; }
    public ManagementAccount save(ManagementAccount x) { var d=new ManagementAccountDO(); d.id=x.id(); d.username=x.username(); d.passwordHash=x.passwordHash(); d.createdAt=x.createdAt(); mapper.insert(d); return x; }
    public Optional<ManagementAccount> findByUsername(String u) { return Optional.ofNullable(mapper.byUsername(u)).map(PersistenceMapping::account); }
    public long count() { return mapper.countRows(); }
}
@Repository
class MybatisApplicationRepository implements ApplicationRepository {
    private final ApplicationMapper mapper; MybatisApplicationRepository(ApplicationMapper mapper) { this.mapper=mapper; }
    public Application save(Application x) { var d=new ApplicationDO(); d.id=x.id(); d.tenantId=x.tenantId(); d.clientId=x.clientId(); d.clientSecretHash=x.clientSecretHash(); d.name=x.name(); d.createdAt=x.createdAt(); mapper.insert(d); return x; }
    public void addRedirectUri(UUID id,String uri) { mapper.addRedirect(id,uri); }
    public java.util.List<String> redirectUris(UUID id) { return mapper.redirectUris(id); }
    public boolean hasRedirectUri(UUID id,String uri) { return mapper.hasRedirect(id,uri)>0; }
    public Optional<Application> findByClientId(String id) { return Optional.ofNullable(mapper.byClientId(id)).map(PersistenceMapping::application); }
    public Optional<Application> findById(UUID id) { return Optional.ofNullable(mapper.byId(id)).map(PersistenceMapping::application); }
    public void addApiResource(UUID applicationId, UUID resourceId) { mapper.addApiResource(applicationId, resourceId); }
    public boolean hasApiResource(UUID applicationId, UUID resourceId) { return mapper.hasApiResource(applicationId, resourceId) > 0; }
}
@Repository
class MybatisUserRepository implements UserRepository {
    private final UserMapper mapper;
    private final UserRoleMapper userRoles;
    MybatisUserRepository(UserMapper mapper, UserRoleMapper userRoles) {
        this.mapper = mapper;
        this.userRoles = userRoles;
    }
    public User save(User x) {
        var d = new UserDO();
        d.id=x.id(); d.tenantId=x.tenantId(); d.username=x.username();
        d.passwordHash=x.passwordHash(); d.enabled=x.enabled(); d.createdAt=x.createdAt();
        mapper.insert(d);
        return x;
    }
    public Optional<User> findByTenantAndUsername(UUID tenantId,String username) {
        return Optional.ofNullable(mapper.byTenantUsername(tenantId,username)).map(PersistenceMapping::user);
    }
    public Optional<User> findById(UUID id) { return Optional.ofNullable(mapper.selectById(id)).map(PersistenceMapping::user); }
    public void updateEnabled(UUID userId, boolean enabled) { mapper.updateEnabled(userId, enabled); }
    public boolean hasRole(UUID userId, UUID roleId) { return userRoles.exists(userId, roleId) > 0; }
    public void addRole(UUID userId, UUID roleId) { userRoles.insert(userId, roleId); }
    public void removeRole(UUID userId, UUID roleId) { userRoles.delete(userId, roleId); }
}

@Repository
class MybatisApiResourceRepository implements ApiResourceRepository {
    private final ApiResourceMapper mapper;
    MybatisApiResourceRepository(ApiResourceMapper mapper) { this.mapper = mapper; }
    public ApiResource save(ApiResource x) {
        var d = new ApiResourceDO();
        d.id = x.id(); d.tenantId = x.tenantId(); d.indicator = x.indicator(); d.name = x.name(); d.createdAt = x.createdAt();
        mapper.insert(d);
        return x;
    }
    public Optional<ApiResource> findByIndicator(String indicator) {
        return Optional.ofNullable(mapper.byIndicator(indicator)).map(PersistenceMapping::apiResource);
    }
    public void addScope(UUID resourceId, String scope) { mapper.addScope(resourceId, scope); }
    public boolean hasScope(UUID resourceId, String scope) { return mapper.hasScope(resourceId, scope) > 0; }
    public java.util.List<String> scopes(UUID resourceId) { return mapper.scopes(resourceId); }
    public void removeScope(UUID resourceId, String scope) { mapper.removeScope(resourceId, scope); }
}
@Repository
class MybatisPermissionRepository implements PermissionRepository {
    private final PermissionMapper mapper;
    MybatisPermissionRepository(PermissionMapper mapper) { this.mapper = mapper; }
    public Permission save(Permission x) {
        var d = new PermissionDO();
        d.id = x.id(); d.tenantId = x.tenantId(); d.resourceId = x.resourceId();
        d.name = x.name(); d.description = x.description(); d.createdAt = x.createdAt();
        mapper.insert(d);
        return x;
    }
    public Optional<Permission> findByTenantResourceAndName(UUID tenantId, UUID resourceId, String name) {
        return Optional.ofNullable(mapper.byTenantResourceName(tenantId, resourceId, name)).map(PersistenceMapping::permission);
    }
}
@Repository
class MybatisRoleRepository implements RoleRepository {
    private final RoleMapper mapper;
    private final RolePermissionMapper rolePermissions;
    MybatisRoleRepository(RoleMapper mapper, RolePermissionMapper rolePermissions) {
        this.mapper = mapper;
        this.rolePermissions = rolePermissions;
    }
    public io.zhijun.roseto.domain.api.Role save(io.zhijun.roseto.domain.api.Role x) {
        var d = new RoleDO();
        d.id = x.id(); d.tenantId = x.tenantId(); d.name = x.name();
        d.description = x.description(); d.createdAt = x.createdAt();
        mapper.insert(d);
        return x;
    }
    public Optional<io.zhijun.roseto.domain.api.Role> findByTenantAndName(UUID tenantId, String name) {
        return Optional.ofNullable(mapper.byTenantName(tenantId, name)).map(PersistenceMapping::role);
    }
    public boolean hasPermission(UUID roleId, UUID permissionId) {
        return rolePermissions.exists(roleId, permissionId) > 0;
    }
    public void addPermission(UUID roleId, UUID permissionId) {
        rolePermissions.insert(roleId, permissionId);
    }
}
@Repository
class MybatisVerificationRepository implements VerificationRepository {
    private final VerificationMapper mapper;
    MybatisVerificationRepository(VerificationMapper mapper) { this.mapper = mapper; }
    public Verification save(Verification x) {
        var d = new VerificationDO();
        d.id=x.id(); d.tenantId=x.tenantId(); d.userId=x.userId(); d.interactionId=x.interactionId(); d.purpose=x.purpose();
        d.identifier=x.identifier(); d.codeHash=x.codeHash(); d.expiresAt=x.expiresAt();
        d.attempts=x.attempts(); d.maxAttempts=x.maxAttempts(); d.status=x.status().name();
        d.createdAt=java.time.Instant.now(); d.updatedAt=java.time.Instant.now();
        if (mapper.byIdAndTenant(x.tenantId(), x.id()) == null) mapper.insert(d); else mapper.updateById(d);
        return x;
    }
    public Optional<Verification> findByIdAndTenant(UUID tenantId, UUID verificationId) {
        return Optional.ofNullable(mapper.byIdAndTenant(tenantId, verificationId)).map(PersistenceMapping::verification);
    }
}
@Repository
class MybatisUserSessionRepository implements UserSessionRepository {
    private final UserSessionMapper mapper;
    MybatisUserSessionRepository(UserSessionMapper mapper) { this.mapper = mapper; }
    public UserSession save(UserSession x) {
        var d = new UserSessionDO();
        d.id=x.id(); d.tenantId=x.tenantId(); d.userId=x.userId(); d.device=x.device();
        d.createdAt=x.createdAt(); d.lastSeenAt=x.lastSeenAt(); d.revokedAt=x.revokedAt();
        if (mapper.selectById(x.id()) == null) mapper.insert(d); else mapper.updateById(d);
        return x;
    }
    public Optional<UserSession> findByIdAndUser(UUID tenantId, UUID userId, UUID sessionId) {
        return Optional.ofNullable(mapper.byIdAndUser(tenantId, userId, sessionId)).map(PersistenceMapping::userSession);
    }
    public java.util.List<UserSession> findByUser(UUID tenantId, UUID userId) {
        return mapper.byUser(tenantId, userId).stream().map(PersistenceMapping::userSession).toList();
    }
    public void revokeByUser(UUID tenantId, UUID userId, java.time.Instant revokedAt) {
        mapper.revokeByUser(tenantId, userId, revokedAt);
    }
}
@Repository
class MybatisExperienceInteractionRepository implements ExperienceInteractionRepository {
    private final ExperienceInteractionMapper mapper;

    MybatisExperienceInteractionRepository(ExperienceInteractionMapper mapper) {
        this.mapper = mapper;
    }

    public io.zhijun.roseto.domain.interaction.ExperienceInteraction save(
            io.zhijun.roseto.domain.interaction.ExperienceInteraction interaction) {
        var row = new ExperienceInteractionDO();
        row.id = interaction.id(); row.tenantId = interaction.tenantId(); row.identifier = interaction.identifier();
        row.mode = interaction.mode(); row.status = interaction.status().name();
        row.createdAt = interaction.createdAt(); row.updatedAt = interaction.updatedAt();
        row.clientId = interaction.clientId(); row.redirectUri = interaction.redirectUri();
        row.codeChallenge = interaction.codeChallenge(); row.nonce = interaction.nonce();
        row.scope = interaction.scope(); row.resource = interaction.resource();
        if (mapper.selectById(interaction.id()) == null) mapper.insert(row); else mapper.updateById(row);
        return interaction;
    }

    public java.util.Optional<io.zhijun.roseto.domain.interaction.ExperienceInteraction> findByIdAndTenant(
            java.util.UUID tenantId, java.util.UUID interactionId) {
        return java.util.Optional.ofNullable(mapper.byIdAndTenant(tenantId, interactionId))
            .map(PersistenceMapping::interaction);
    }
}
@Repository
class MybatisRefreshTokenRepository implements RefreshTokenRepository {
    private final RefreshTokenMapper mapper;
    MybatisRefreshTokenRepository(RefreshTokenMapper mapper) { this.mapper = mapper; }
    public void save(String token, io.zhijun.roseto.domain.oidc.RefreshGrant grant) {
        var row = new RefreshTokenDO();
        row.token = token; row.userId = UUID.fromString(grant.userId()); row.clientId = grant.clientId();
        row.tenantId = UUID.fromString(grant.tenantId()); row.scope = grant.scope(); row.resource = grant.resource();
        row.revoked = false; row.createdAt = java.time.Instant.now(); row.expiresAt = grant.expiresAt();
        mapper.insert(row);
    }
    public synchronized Optional<io.zhijun.roseto.domain.oidc.RefreshGrant> consume(String token, String clientId, String tenantId) {
        var row = mapper.byToken(token);
        if (row == null || row.revoked || !row.clientId.equals(clientId) || !row.tenantId.toString().equals(tenantId)
            || !row.expiresAt.isAfter(java.time.Instant.now())) return Optional.empty();
        mapper.revoke(token);
        return Optional.of(new io.zhijun.roseto.domain.oidc.RefreshGrant(row.userId.toString(), row.clientId,
            row.tenantId.toString(), row.scope, row.resource, row.expiresAt));
    }
    public boolean wasUsed(String token) { return mapper.wasUsed(token) > 0; }
    public void revokeByClient(String clientId) { mapper.revokeByClient(clientId); }
    public void revoke(String token) { mapper.revoke(token); }
}
