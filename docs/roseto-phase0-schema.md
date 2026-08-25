# Roseto Phase 0 数据库表结构

## 1. 通用约束

- PostgreSQL 17+。
- 所有时间使用 `timestamptz`，应用和数据库统一 UTC。
- UUID 由应用生成并在数据库中校验。
- 所有租户业务表包含 `tenant_id`。
- 删除策略默认 `RESTRICT`；可异步清理的资源使用显式状态，不使用隐式级联。
- 密码、authorization code、refresh token 和私钥不得以明文存储。

## 2. 表定义

### tenants

| 列 | 类型 | 约束 |
|---|---|---|
| id | uuid | PK |
| public_tenant_key | varchar(64) | NOT NULL, UNIQUE |
| name | varchar(256) | NOT NULL |
| issuer | varchar(512) | NOT NULL, UNIQUE |
| status | varchar(32) | NOT NULL, `active` / `disabled` |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

### users

| 列 | 类型 | 约束 |
|---|---|---|
| id | uuid | PK |
| tenant_id | uuid | FK tenants, NOT NULL |
| status | varchar(32) | NOT NULL, `active` / `disabled` |
| primary_email | varchar(320) | NULL |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

索引：`(tenant_id, id)`、`(tenant_id, primary_email)`。

### password_credentials

| 列 | 类型 | 约束 |
|---|---|---|
| user_id | uuid | PK, FK users |
| password_hash | varchar(512) | NOT NULL |
| algorithm | varchar(32) | NOT NULL, `argon2id` |
| changed_at | timestamptz | NOT NULL |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

### applications

| 列 | 类型 | 约束 |
|---|---|---|
| id | uuid | PK |
| tenant_id | uuid | FK tenants, NOT NULL |
| client_id | varchar(128) | NOT NULL, UNIQUE |
| name | varchar(256) | NOT NULL |
| type | varchar(32) | NOT NULL, Phase 0 为 `web` |
| token_endpoint_auth_method | varchar(64) | NOT NULL, Phase 0 为 `none` |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

索引：`(tenant_id, id)`、`(tenant_id, client_id)`。

### redirect_uris

| 列 | 类型 | 约束 |
|---|---|---|
| id | uuid | PK |
| application_id | uuid | FK applications, NOT NULL |
| uri | varchar(2048) | NOT NULL |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

约束：`UNIQUE(application_id, uri)`。

### api_resources

| 列 | 类型 | 约束 |
|---|---|---|
| id | uuid | PK |
| tenant_id | uuid | FK tenants, NOT NULL |
| indicator | varchar(512) | NOT NULL |
| name | varchar(256) | NOT NULL |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

约束：`UNIQUE(tenant_id, indicator)`。Phase 0 每个 Tenant 只创建一个固定 Resource。

### permissions

| 列 | 类型 | 约束 |
|---|---|---|
| id | uuid | PK |
| tenant_id | uuid | FK tenants, NOT NULL |
| resource_id | uuid | FK api_resources, NOT NULL |
| name | varchar(128) | NOT NULL |
| description | varchar(512) | NULL |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

约束：`UNIQUE(tenant_id, resource_id, name)`。

### authorization_codes

| 列 | 类型 | 约束 |
|---|---|---|
| id | uuid | PK |
| tenant_id | uuid | FK tenants, NOT NULL |
| code_hash | varchar(512) | NOT NULL, UNIQUE |
| application_id | uuid | FK applications, NOT NULL |
| user_id | uuid | FK users, NOT NULL |
| redirect_uri | varchar(2048) | NOT NULL |
| code_challenge | varchar(128) | NOT NULL |
| code_challenge_method | varchar(16) | NOT NULL, `S256` |
| nonce | varchar(512) | NULL |
| status | varchar(32) | NOT NULL, `created` / `consumed` / `expired` / `rejected` |
| expires_at | timestamptz | NOT NULL |
| consumed_at | timestamptz | NULL |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

索引：`(tenant_id, code_hash)`、`(status, expires_at)`。

### user_sessions

| 列 | 类型 | 约束 |
|---|---|---|
| id | uuid | PK |
| tenant_id | uuid | FK tenants, NOT NULL |
| user_id | uuid | FK users, NOT NULL |
| status | varchar(32) | NOT NULL, `active` / `revoked` / `expired` |
| expires_at | timestamptz | NOT NULL |
| revoked_at | timestamptz | NULL |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

索引：`(tenant_id, user_id, status)`、`(status, expires_at)`。

### signing_keys

| 列 | 类型 | 约束 |
|---|---|---|
| id | uuid | PK |
| tenant_id | uuid | FK tenants, NOT NULL |
| kid | varchar(128) | NOT NULL, UNIQUE |
| algorithm | varchar(32) | NOT NULL |
| status | varchar(32) | NOT NULL, `active` / `verification_only` / `retired` |
| public_jwk | jsonb | NOT NULL |
| encrypted_private_key | text | NOT NULL |
| activated_at | timestamptz | NOT NULL |
| retired_at | timestamptz | NULL |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

约束：每个 Tenant 最多一个 `active` key。生产环境的加密密钥由 KMS/Vault 提供。

## 3. Migration 顺序

```text
V1 tenants
V2 users
V3 password_credentials
V4 applications
V5 redirect_uris
V6 api_resources
V7 permissions
V8 authorization_codes
V9 user_sessions
V10 signing_keys
V11 Phase 0 indexes and constraints
V12 management_accounts
V13 bootstrap seed
```

## 4. 不在 Phase 0 的表

```text
refresh_token_families
organizations
organization_members
connectors
verifications
mfa_factors
subscriptions
usage_events
platform_accounts
```

这些表只能在对应 Phase 开始前新增 migration，不提前创建空壳模块或空表。

## 5. Phase 0 Scope 决策

Phase 0 不实现 Role 和 permission assignment。`permissions` 表保留为 API Resource 的描述目录，但不作为 Token scope 授权来源。

Phase 0 scope 为代码中的受控常量：

```text
openid
api:read
api:write
```

`api:read` 和 `api:write` 均绑定唯一 Resource indicator：

```text
https://api.roseto.example
```

Phase 1 再新增 `roles`、`role_permissions`、`user_roles` 或等价授权表，并通过新的 migration 引入数据库授权来源。

## 6. Session 字段补充

`user_sessions` 增加：

```text
application_id uuid not null references applications(id)
session_handle_hash varchar(512) not null unique
```

Session cookie 只保存 opaque handle；数据库不保存 cookie 原值。

## 7. Signing Key 加密合同

`encrypted_private_key` 使用 envelope encryption：

```text
ciphertext: base64
algorithm: AES-256-GCM
nonce: 12 bytes
authentication_tag: 16 bytes
kek_reference: KMS/Vault key identifier
key_version: provider key version
```

建议将加密载荷存为 JSONB：

```json
{
  "version": 1,
  "algorithm": "AES-256-GCM",
  "ciphertext": "...",
  "nonce": "...",
  "tag": "...",
  "kekReference": "...",
  "keyVersion": "..."
}
```

`encrypted_private_key` 的数据库内容不得写入日志、错误响应或测试快照。生产环境启动时无法从 KMS/Vault 解密 active key，应用必须 fail fast，不得生成临时 key 继续运行。

## 8. Management Accounts

Phase 0 增加实例级管理主体表：

```text
management_accounts
  id uuid primary key
  tenant_id uuid not null references tenants(id)
  username varchar(256) not null
  password_hash varchar(512) not null
  status varchar(32) not null -- active, disabled
  created_at timestamptz not null
  updated_at timestamptz not null
  unique(tenant_id, username)
```

`password_hash` 使用 Argon2id。管理账号密码与 Tenant End User 密码分离；禁止明文、可逆加密或日志输出。`/api/management/token` 从该表签发 audience 为 `roseto-management` 的短期 Management Token。

## 9. Logto-compatible mode 覆盖规则

本文件第 2–4 节描述的是 Native Roseto Phase 0 最小 schema。若启用 `Logto-compatible mode`，以 [`roseto-logto-database-compatibility.md`](roseto-logto-database-compatibility.md) 为准：必须导入完整 Logto OSS schema、alteration、RLS、触发器和 base seed；本文件的最小表清单不构成兼容模式的完整表清单。
