# Roseto Phase 0 API 合同

## 1. URL

```text
issuer = https://{host}/t/{publicTenantKey}
```

所有 OIDC endpoint 均相对于 issuer。Tenant 不从客户端 query 参数读取。

## 2. Discovery

```http
GET /.well-known/openid-configuration
```

响应 `200 application/json`：

```json
{
  "issuer": "https://auth.example.com/t/acme",
  "authorization_endpoint": "https://auth.example.com/t/acme/authorize",
  "token_endpoint": "https://auth.example.com/t/acme/token",
  "userinfo_endpoint": "https://auth.example.com/t/acme/userinfo",
  "jwks_uri": "https://auth.example.com/t/acme/jwks",
  "response_types_supported": ["code"],
  "subject_types_supported": ["public"],
  "id_token_signing_alg_values_supported": ["RS256"],
  "scopes_supported": ["openid", "api:read", "api:write"],
  "claims_supported": ["iss", "sub", "aud", "iat", "exp", "nonce"]
}
```

## 3. JWKS

```http
GET /jwks
```

响应 `200 application/json`：

```json
{"keys":[{"kty":"RSA","kid":"key-1","use":"sig","alg":"RS256","n":"...","e":"AQAB"}]}
```

响应必须禁止缓存私钥；JWKS 可短时 public cache，但轮换后必须及时刷新。

## 4. Authorization

```http
GET /authorize?client_id=...&redirect_uri=...&response_type=code&scope=openid&state=...&code_challenge=...&code_challenge_method=S256&nonce=...
```

必需参数：`client_id`、`redirect_uri`、`response_type=code`、`scope`、`state`、`code_challenge`、`code_challenge_method=S256`。

成功响应：向已注册 `redirect_uri` 重定向：

```text
302 Location: {redirect_uri}?code={one-time-code}&state={state}
```

拒绝条件：未知 client、URI 不精确匹配、非 code response type、缺少 openid、非 S256、用户拒绝、Tenant 不存在。

拒绝时不得重定向到未注册 URI。可安全重定向时使用：

```text
?error=access_denied&state={original-state}
```

## 5. Token

```http
POST /token
Content-Type: application/x-www-form-urlencoded
```

请求：

```text
grant_type=authorization_code
client_id={client-id}
code={authorization-code}
redirect_uri={exact-redirect-uri}
code_verifier={pkce-verifier}
```

成功 `200`：

```json
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 300,
  "scope": "openid api:read",
  "id_token": "eyJ..."
}
```

响应必须包含 `Cache-Control: no-store` 和 `Pragma: no-cache`。

错误 `400`：

```json
{"error":"invalid_grant","error_description":"The authorization code is invalid."}
```

Phase 0 不支持 `refresh_token` grant。

## 6. UserInfo

```http
GET /userinfo
Authorization: Bearer {access-token}
```

成功 `200`：

```json
{"sub":"user-subject"}
```

无效、过期、issuer 或 audience 错误的 Token 返回 `401`，不得返回用户资料。

## 7. Logout

```http
GET /logout?id_token_hint=...&post_logout_redirect_uri=...&state=...
```

`post_logout_redirect_uri` 必须与 Application 注册值精确匹配。成功后撤销当前 Session，并在允许时返回 `302`；不允许重定向时返回 `204`。

## 8. Management API

Phase 0 的管理 API 使用本地管理认证，不复用 OIDC End User Token：

```http
POST /api/tenants
POST /api/tenants/{tenantId}/applications
POST /api/tenants/{tenantId}/users
POST /api/tenants/{tenantId}/resources
POST /api/tenants/{tenantId}/permissions
```

创建 Application：

```json
{
  "name":"Demo Web",
  "type":"web",
  "redirectUris":["http://localhost:8080/callback"],
  "resourceIndicator":"https://api.example.com"
}
```

创建 User：

```json
{"email":"user@example.com","password":"not-returned"}
```

成功创建返回 `201`；重复 client、URI、resource 或 email 返回 `409`；Tenant 不存在返回 `404`。

## 9. 错误合同

```json
{"code":"oauth.invalid_grant","message":"The authorization code is invalid.","requestId":"request-id"}
```

OAuth endpoint 使用 OAuth 标准 `error` 字段；Management API 使用 `code`。不得在日志或响应中暴露密码、私钥、authorization code 原值。

## 10. 状态码

| 场景 | 状态 |
|---|---:|
| 成功读取 | 200 |
| 成功创建 | 201 |
| 成功无内容 | 204 |
| 参数错误 | 400 |
| 缺少认证或 Token 无效 | 401 |
| 已认证但无权限 | 403 |
| 资源不存在 | 404 |
| 冲突 | 409 |
| 限流 | 429 |
| 未预期服务错误 | 500 |

## 11. 管理 API 认证合同（修正版）

Phase 0 管理 API 使用独立的 Management Access Token，不接受 End User Access Token。

### Bootstrap

```text
首次启动：BOOTSTRAP_TOKEN 环境变量或 secret mount
成功创建第一个 Tenant 和 Tenant Admin 后：立即使 bootstrap token 失效
重复使用：401 management.bootstrap_used
```

### Management Token

```text
aud = roseto-management
sub = administrator subject
tenant_id = 当前管理 Tenant
scope = tenant:read tenant:write application:write user:write resource:write
exp = 签发后 15 分钟
```

请求：

```http
Authorization: Bearer <management-access-token>
```

Management API 必须验证 `iss`、`aud`、签名、`exp`、`tenant_id` 和 scope。Tenant ID 必须来自 Token 上下文；请求路径中的 tenant ID 只能作为一致性检查，不能扩大权限。

错误：

```text
缺少或无效 Token       401 management.unauthorized
scope 不足             403 management.forbidden
Token tenant 与路径不同 403 tenant.context_mismatch
bootstrap 已使用       401 management.bootstrap_used
```

## 12. Scope、Resource 和 UserInfo 规则（修正版）

Phase 0 固定以下 scope，不创建 Role 表，也不创建 permission assignment 表：

```text
openid
api:read
api:write
```

`openid` 是 OIDC protocol scope，只决定是否签发 ID Token；它不授予 Resource Server 业务权限。

Phase 0 固定一个 API Resource：

```text
https://api.roseto.example
```

scope 绑定：

```text
api:read  → https://api.roseto.example
api:write → https://api.roseto.example
```

未知 scope、未注册 scope、未授权 scope 必须返回 `invalid_scope`。授权请求中没有 `api:read` 或 `api:write` 时，Access Token 的 `scope` 不包含它们。

UserInfo 使用独立 audience：

```text
https://auth.example.com/t/{publicTenantKey}/userinfo
```

只有 audience 为该 UserInfo endpoint 的 Access Token 可以访问 `/userinfo`。Resource Server Access Token 不得直接用于 UserInfo。

## 13. Logout Session 合同（修正版）

成功登录后，Hosted Sign-in 创建 HttpOnly Session Cookie：

```text
名称：ROSETO_SESSION
Secure：true（开发环境允许 localhost 例外）
HttpOnly：true
SameSite：Lax
Path：/t/{publicTenantKey}
```

Cookie 值是随机 opaque session handle；数据库只保存 hash。`user_sessions` 必须关联：

```text
tenant_id
user_id
application_id
session_handle_hash
```

`/logout` 的 Session 定位顺序：

```text
当前 ROSETO_SESSION cookie
→ 校验 id_token_hint 的 iss、aud、sub（如果提供）
→ 校验 post_logout_redirect_uri
→ 撤销当前 Session
```

没有有效 Session 时仍返回成功结果，但不得撤销其他用户或其他 Application 的 Session。

## 14. Management Token 签发

Phase 0 使用本地 Management Account，不把 Tenant End User 当作管理主体。

```http
POST /api/management/token
Content-Type: application/x-www-form-urlencoded
```

请求：

```text
username={bootstrap-created-admin}
password={admin-password}
grant_type=password
```

成功 `200`：

```json
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "tenant:read tenant:write application:write user:write resource:write"
}
```

该 endpoint 只允许本地 Management Account，Phase 0 不提供通用 OAuth password grant。连续失败必须限流；不存在账号和错误密码返回相同错误。

Bootstrap 必须创建第一个 Management Account，并只在初始化响应或受保护 secret channel 中交付初始凭据；初始密码不得写入数据库明文。
