package io.zhijun.roseto.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

public final class PersistenceEntities {
    private PersistenceEntities() {}
    @TableName("tenants") public static class TenantDO {
        @TableId public UUID id; @TableField("public_key") public String publicKey; public String name; public String issuer; @TableField("created_at") public Instant createdAt;
    }
    @TableName("management_accounts") public static class ManagementAccountDO {
        @TableId public UUID id; public String username; @TableField("password_hash") public String passwordHash; @TableField("created_at") public Instant createdAt;
    }
    @TableName("applications") public static class ApplicationDO {
        @TableId public UUID id; @TableField("tenant_id") public UUID tenantId; @TableField("client_id") public String clientId; @TableField("client_secret_hash") public String clientSecretHash; public String name; @TableField("created_at") public Instant createdAt;
    }
    @TableName("api_resources") public static class ApiResourceDO {
        @TableId public UUID id; @TableField("tenant_id") public UUID tenantId; public String indicator; public String name; @TableField("created_at") public Instant createdAt;
    }
    @TableName("permissions") public static class PermissionDO {
        @TableId public UUID id; @TableField("tenant_id") public UUID tenantId; @TableField("resource_id") public UUID resourceId;
        public String name; public String description; @TableField("created_at") public Instant createdAt;
    }
    @TableName("roles") public static class RoleDO {
        @TableId public UUID id; @TableField("tenant_id") public UUID tenantId;
        public String name; public String description; @TableField("created_at") public Instant createdAt;
    }
    @TableName("verification_records") public static class VerificationDO {
        @TableId public UUID id; @TableField("tenant_id") public UUID tenantId; @TableField("user_id") public UUID userId;
        @TableField("interaction_id") public UUID interactionId;
        public String purpose; public String identifier; @TableField("code_hash") public String codeHash;
        @TableField("expires_at") public Instant expiresAt; public int attempts; @TableField("max_attempts") public int maxAttempts;
        public String status; @TableField("created_at") public Instant createdAt; @TableField("updated_at") public Instant updatedAt;
    }
    @TableName("refresh_tokens") public static class RefreshTokenDO {
        @TableId public String token; @TableField("user_id") public UUID userId; @TableField("client_id") public String clientId;
        @TableField("tenant_id") public UUID tenantId; public String scope; public String resource; public boolean revoked;
        @TableField("created_at") public Instant createdAt; @TableField("expires_at") public Instant expiresAt;
    }
    @TableName("users") public static class UserDO {
        @TableId public UUID id; @TableField("tenant_id") public UUID tenantId; public String username; @TableField("password_hash") public String passwordHash; public boolean enabled; @TableField("created_at") public Instant createdAt;
    }
    @TableName("user_sessions") public static class UserSessionDO {
        @TableId public UUID id; @TableField("tenant_id") public UUID tenantId; @TableField("user_id") public UUID userId;
        public String device; @TableField("created_at") public Instant createdAt; @TableField("last_seen_at") public Instant lastSeenAt;
        @TableField("revoked_at") public Instant revokedAt;
    }
    @TableName("experience_interactions") public static class ExperienceInteractionDO {
        @TableId public UUID id; @TableField("tenant_id") public UUID tenantId;
        public String identifier; public String mode; public String status;
        @TableField("created_at") public Instant createdAt; @TableField("updated_at") public Instant updatedAt;
        @TableField("client_id") public String clientId; @TableField("redirect_uri") public String redirectUri;
        @TableField("code_challenge") public String codeChallenge; public String nonce; public String scope; public String resource;
    }
}
