# Roseto Cloud Control Plane 数据库表结构

## 1. 边界

这些表属于 Cloud Control Plane，不存储 Tenant 内部密码、OIDC authorization code 或业务用户资料。所有表必须有审计时间；所有 Tenant 相关表必须有 `tenant_id`。

## 2. 表结构

### platform_accounts

```text
id uuid primary key
subject varchar(256) not null unique
status varchar(32) not null -- active, disabled
created_at timestamptz not null
updated_at timestamptz not null
```

### platform_tenant_memberships

```text
platform_account_id uuid not null references platform_accounts(id)
tenant_id uuid not null references tenants(id)
role varchar(64) not null
status varchar(32) not null
created_at timestamptz not null
updated_at timestamptz not null
primary key(platform_account_id, tenant_id)
```

### tenant_provisioning_jobs

```text
id uuid primary key
tenant_id uuid not null references tenants(id)
state varchar(32) not null
attempts integer not null default 0
last_error_code varchar(128)
next_attempt_at timestamptz
created_at timestamptz not null
updated_at timestamptz not null
```

### tenant_invitations

```text
id uuid primary key
tenant_id uuid not null references tenants(id)
invitee varchar(320) not null
role varchar(64) not null
token_hash varchar(512) not null unique
status varchar(32) not null
expires_at timestamptz not null
accepted_user_id uuid
created_at timestamptz not null
updated_at timestamptz not null
```

### tenant_regions

```text
tenant_id uuid primary key references tenants(id)
region_name varchar(128) not null
status varchar(32) not null
external_reference varchar(256)
created_at timestamptz not null
updated_at timestamptz not null
```

### subscriptions

```text
id uuid primary key
tenant_id uuid not null references tenants(id)
plan_id varchar(128) not null
status varchar(32) not null
current_period_start timestamptz not null
current_period_end timestamptz not null
created_at timestamptz not null
updated_at timestamptz not null
```

约束：同一 Tenant 只能有一个当前 subscription。

### quota_assignments

```text
tenant_id uuid not null references tenants(id)
usage_key varchar(128) not null
limit_value bigint
source varchar(32) not null -- plan, addon, override
created_at timestamptz not null
updated_at timestamptz not null
primary key(tenant_id, usage_key)
```

### usage_events

```text
id uuid primary key
tenant_id uuid not null references tenants(id)
usage_key varchar(128) not null
quantity bigint not null
occurred_at timestamptz not null
idempotency_key varchar(256) not null unique
created_at timestamptz not null
```

### usage_aggregates

```text
tenant_id uuid not null references tenants(id)
usage_key varchar(128) not null
period_start date not null
period_end date not null
quantity bigint not null
updated_at timestamptz not null
primary key(tenant_id, usage_key, period_start, period_end)
```

## 3. 约束和索引

```text
platform_tenant_memberships(tenant_id, status)
tenant_invitations(tenant_id, status, expires_at)
tenant_provisioning_jobs(state, next_attempt_at)
subscriptions(tenant_id, status)
usage_events(tenant_id, usage_key, occurred_at)
usage_aggregates(tenant_id, period_start, period_end)
```

- invitation token 只保存 hash。
- 相同 idempotency key 配不同请求摘要必须拒绝。
- `usage_events.quantity` 必须禁止负数，除非使用明确的 adjustment event 类型。
- 删除 Tenant 前必须完成 provisioning、membership 和 subscription 状态迁移。

## 4. Migration 顺序

```text
C1 platform_accounts
C2 platform_tenant_memberships
C3 tenant_provisioning_jobs
C4 tenant_invitations
C5 tenant_regions
C6 subscriptions
C7 quota_assignments
C8 usage_events
C9 usage_aggregates
C10 indexes, constraints and audit events
C11 idempotency_keys
```

## 5. 幂等键

创建 Tenant 和写入 Usage Event 必须使用独立幂等记录：

```text
idempotency_keys
  scope varchar(128) not null
  key_value varchar(256) not null
  request_hash varchar(512) not null
  response_status integer not null
  response_body jsonb not null
  resource_id uuid
  expires_at timestamptz not null
  created_at timestamptz not null
  primary key(scope, key_value)
```

同一 `(scope, key_value)`：

- `request_hash` 相同：返回已保存响应；
- `request_hash` 不同：返回 `409 cloud.idempotency_conflict`；
- 记录未过期前不能再次执行副作用。

幂等记录必须与业务写入处于同一数据库事务中。

## 6. 并发版本

以下可变表增加：

```text
version bigint not null default 0
```

适用表：

```text
tenants
subscriptions
quota_assignments
tenant_regions
```

PATCH 使用 `If-Match: "<version>"` 或请求体 `version`，二者必须择一。更新条件必须包含旧 version，成功后 version 加一；影响行数为零时返回 `409 cloud.version_conflict`。

## 7. Subscription 约束

数据库必须保证一个 Tenant 只有一个当前订阅。当前订阅定义为 `trialing`、`active` 或 `past_due`：

```sql
CREATE UNIQUE INDEX subscriptions_one_current
ON subscriptions (tenant_id)
WHERE status IN ('trialing', 'active', 'past_due');
```

## 8. Audit Event

Billing、Quota、Membership、Tenant 删除和 Region 变更必须写入：

```text
audit_events
  id uuid primary key
  tenant_id uuid
  actor_type varchar(32) not null
  actor_id varchar(256) not null
  action varchar(128) not null
  resource_type varchar(128) not null
  resource_id varchar(256)
  before_data jsonb
  after_data jsonb
  request_id varchar(256) not null
  occurred_at timestamptz not null
```

`before_data` 和 `after_data` 不得包含密码、Token、私钥或完整邀请 token。

## 9. Usage Event 类型

`usage_events` 增加：

```text
event_type varchar(32) not null -- usage 或 adjustment
```

约束：

```text
usage      → quantity > 0
adjustment → quantity 可正可负，但必须由 Billing Operator 创建并有 audit_event
```
