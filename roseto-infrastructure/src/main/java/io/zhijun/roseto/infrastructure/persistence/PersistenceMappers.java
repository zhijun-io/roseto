package io.zhijun.roseto.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.UUID;
import static io.zhijun.roseto.infrastructure.persistence.PersistenceEntities.*;

@Mapper interface TenantMapper extends BaseMapper<TenantDO> {
    @Select("select id, public_key, name, issuer, created_at from tenants where id = #{id}") TenantDO byId(UUID id);
    @Select("select id, public_key, name, issuer, created_at from tenants where public_key = #{key}") TenantDO byKey(String key);
    @Select("select id, public_key, name, issuer, created_at from tenants order by created_at limit 1") TenantDO first();
    @Select("select count(*) from tenants") long countRows();
}
@Mapper interface ManagementAccountMapper extends BaseMapper<ManagementAccountDO> {
    @Select("select id, username, password_hash, created_at from management_accounts where username = #{username}") ManagementAccountDO byUsername(String username);
    @Select("select count(*) from management_accounts") long countRows();
}
@Mapper interface ApplicationMapper extends BaseMapper<ApplicationDO> {
    @Insert("insert into redirect_uris (application_id, redirect_uri) values (#{applicationId}, #{redirectUri})") int addRedirect(UUID applicationId, String redirectUri);
    @Select("select count(*) from redirect_uris where application_id = #{applicationId} and redirect_uri = #{redirectUri}") long hasRedirect(UUID applicationId, String redirectUri);
    @Select("select redirect_uri from redirect_uris where application_id = #{applicationId} order by redirect_uri") java.util.List<String> redirectUris(UUID applicationId);
    @Select("select id, tenant_id, client_id, client_secret_hash, name, created_at from applications where client_id = #{clientId}") ApplicationDO byClientId(String clientId);
    @Select("select id, tenant_id, client_id, client_secret_hash, name, created_at from applications where id = #{id}") ApplicationDO byId(UUID id);
    @Insert("insert into application_api_resources (application_id, resource_id) values (#{applicationId}, #{resourceId})") int addApiResource(UUID applicationId, UUID resourceId);
    @Select("select count(*) from application_api_resources where application_id = #{applicationId} and resource_id = #{resourceId}") long hasApiResource(UUID applicationId, UUID resourceId);
}
@Mapper interface UserMapper extends BaseMapper<UserDO> {
    @Select("select id, tenant_id, username, password_hash, enabled, created_at from users where tenant_id = #{tenantId} and username = #{username}") UserDO byTenantUsername(UUID tenantId, String username);
    @org.apache.ibatis.annotations.Update("update users set enabled = #{enabled} where id = #{userId}") int updateEnabled(UUID userId, boolean enabled);
}
@Mapper interface UserRoleMapper {
    @Insert("insert into user_roles (user_id, role_id) values (#{userId}, #{roleId})") int insert(UUID userId, UUID roleId);
    @Select("select count(*) from user_roles where user_id = #{userId} and role_id = #{roleId}") long exists(UUID userId, UUID roleId);
    @Delete("delete from user_roles where user_id = #{userId} and role_id = #{roleId}") int delete(UUID userId, UUID roleId);
}
@Mapper interface ApiResourceMapper extends BaseMapper<ApiResourceDO> {
    @Select("select id, tenant_id, indicator, name, created_at from api_resources where indicator = #{indicator}") ApiResourceDO byIndicator(String indicator);
    @Insert("insert into api_resource_scopes (resource_id, name) values (#{resourceId}, #{scope})") int addScope(UUID resourceId, String scope);
    @Select("select count(*) from api_resource_scopes where resource_id = #{resourceId} and name = #{scope}") long hasScope(UUID resourceId, String scope);
    @Delete("delete from api_resource_scopes where resource_id = #{resourceId} and name = #{scope}") int removeScope(UUID resourceId, String scope);
    @Select("select name from api_resource_scopes where resource_id = #{resourceId} order by name") java.util.List<String> scopes(UUID resourceId);
}
@Mapper interface PermissionMapper extends BaseMapper<PermissionDO> {
    @Select("select id, tenant_id, resource_id, name, description, created_at from permissions where tenant_id = #{tenantId} and resource_id = #{resourceId} and name = #{name}") PermissionDO byTenantResourceName(UUID tenantId, UUID resourceId, String name);
}
@Mapper interface RoleMapper extends BaseMapper<RoleDO> {
    @Select("select id, tenant_id, name, description, created_at from roles where tenant_id = #{tenantId} and name = #{name}") RoleDO byTenantName(UUID tenantId, String name);
}
@Mapper interface RolePermissionMapper {
    @Insert("insert into role_permissions (role_id, permission_id) values (#{roleId}, #{permissionId})") int insert(UUID roleId, UUID permissionId);
    @Select("select count(*) from role_permissions where role_id = #{roleId} and permission_id = #{permissionId}") long exists(UUID roleId, UUID permissionId);
}
@Mapper interface VerificationMapper extends BaseMapper<VerificationDO> {
    @Select("select id, tenant_id, user_id, interaction_id, purpose, identifier, code_hash, expires_at, attempts, max_attempts, status, created_at, updated_at from verification_records where tenant_id = #{tenantId} and id = #{verificationId}") VerificationDO byIdAndTenant(UUID tenantId, UUID verificationId);
}
@Mapper interface UserSessionMapper extends BaseMapper<UserSessionDO> {
    @Select("select id, tenant_id, user_id, device, created_at, last_seen_at, revoked_at from user_sessions where tenant_id = #{tenantId} and user_id = #{userId} and id = #{sessionId}") UserSessionDO byIdAndUser(UUID tenantId, UUID userId, UUID sessionId);
    @Select("select id, tenant_id, user_id, device, created_at, last_seen_at, revoked_at from user_sessions where tenant_id = #{tenantId} and user_id = #{userId} order by created_at desc") java.util.List<UserSessionDO> byUser(UUID tenantId, UUID userId);
    @org.apache.ibatis.annotations.Update("update user_sessions set revoked_at = #{revokedAt} where tenant_id = #{tenantId} and user_id = #{userId} and revoked_at is null") int revokeByUser(UUID tenantId, UUID userId, java.time.Instant revokedAt);
}
@Mapper interface ExperienceInteractionMapper extends BaseMapper<ExperienceInteractionDO> {
    @Select("select id, tenant_id, identifier, mode, status, created_at, updated_at, client_id, redirect_uri, code_challenge, nonce, scope, resource from experience_interactions where tenant_id = #{tenantId} and id = #{interactionId}") ExperienceInteractionDO byIdAndTenant(UUID tenantId, UUID interactionId);
}
@Mapper interface RefreshTokenMapper extends BaseMapper<RefreshTokenDO> {
    @Select("select token,user_id,client_id,tenant_id,scope,resource,revoked,created_at,expires_at from refresh_tokens where token = #{token}") RefreshTokenDO byToken(String token);
    @Update("update refresh_tokens set revoked = true where token = #{token}") int revoke(String token);
    @Update("update refresh_tokens set revoked = true where client_id = #{clientId}") int revokeByClient(String clientId);
    @Select("select count(*) from refresh_tokens where token = #{token} and revoked = true") long wasUsed(String token);
}
