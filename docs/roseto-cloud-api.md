# Roseto Cloud Control Plane API 合同

## 1. 认证

Cloud API：

```http
Authorization: Bearer <platform-token>
```

Tenant Organization API：

```http
Authorization: Bearer <organization-token>
```

服务端必须从 Token 的 subject 和 membership 解析权限，不信任请求体中的 role、scope 或 tenantId。

## 2. Tenant API

### List tenants

```http
GET /api/tenants?page=1&page_size=20
```

响应 `200`：

```json
{"data":[],"page":1,"pageSize":20,"total":0}
```

### Create tenant

```http
POST /api/tenants
Idempotency-Key: create-key
Content-Type: application/json
```

```json
{"name":"Demo","regionName":"us","tag":"development","planId":"free"}
```

成功 `201`：

```json
{"id":"tenant-id","name":"Demo","regionName":"us","tag":"development","planId":"free","status":"provisioning"}
```

相同 key 和相同请求摘要返回同一结果；相同 key 和不同请求摘要返回 `409 cloud.idempotency_conflict`。

### Tenant detail

```http
GET /api/tenants/{tenantId}
PATCH /api/tenants/{tenantId}
DELETE /api/tenants/{tenantId}
```

删除返回 `202` 并进入 `deleting`，不得立即假装资源已删除。

## 3. Membership API

```http
GET  /api/tenants/{tenantId}/members
POST /api/tenants/{tenantId}/invitations
PATCH /api/tenants/{tenantId}/members/{userId}/role
DELETE /api/tenants/{tenantId}/members/{userId}
```

邀请请求：

```json
{"invitee":"user@example.com","role":"collaborator","expiresInSeconds":86400}
```

无 membership 返回 `403 cloud.tenant_forbidden`。删除最后一个 Tenant Admin 返回 `409 cloud.last_admin_required`。

## 4. Subscription、Quota、Usage

```http
GET /api/tenants/{tenantId}/subscription
GET /api/tenants/{tenantId}/subscription-usage
GET /api/tenants/{tenantId}/quota
GET /api/tenants/{tenantId}/invoices
GET /api/tenants/{tenantId}/usage?usage_key=users&period_start=...&period_end=...
```

Usage event（仅内部服务或授权 Billing Operator）：

```http
POST /api/tenants/{tenantId}/usage-events
Idempotency-Key: usage-event-key
```

```json
{"usageKey":"users","quantity":1,"occurredAt":"2026-08-11T00:00:00Z"}
```

重复事件不重复累计。

## 5. Region API

```http
GET /api/me/regions
GET /api/regions
```

Tenant 可用 Region 由 Platform membership、Tenant 状态和 Region 状态共同决定，客户端不能任意指定私有 Region。

## 6. 错误合同

```json
{"code":"cloud.tenant_forbidden","message":"Tenant access is forbidden.","requestId":"req-1"}
```

最小错误码：

```text
cloud.unauthenticated             401
cloud.tenant_forbidden            403
cloud.not_found                   404
cloud.idempotency_conflict        409
cloud.last_admin_required         409
cloud.invalid_state               409
cloud.quota_exceeded              429
cloud.invalid_request             400
```

## 7. 分页与并发

- 默认 `page_size=20`，最大 `100`。
- 非法 page 或 page_size 返回 `400`。
- PATCH 支持 `version` 或 `If-Match`，并发版本冲突返回 `409`。
- 所有列表接口必须固定排序，默认按 `created_at ASC, id ASC`。
