# Roseto Phase 0 需求与验收

## 1. 目标

交付一个可被标准 OIDC Client 接入的 Java OIDC Provider。Phase 0 只验证：Tenant、Web Application、密码用户、Authorization Code + PKCE、Discovery、JWKS、ID Token、Access Token 和 Rose Resource Server。

不包含：Refresh Token、社交登录、完整 Console、Organization Token、SAML、SCIM、Billing、Region、Cloud Control Plane。

## 2. 用户角色

| 角色 | 能力 |
|---|---|
| Bootstrap Operator | 一次性创建第一个 Tenant 和管理用户 |
| Tenant Admin | 管理 Application、User、API Resource、Scope |
| End User | 通过 Hosted Sign-in 完成登录 |
| OIDC Client | 发起授权请求并兑换 Token |
| Resource Server | 校验 Access Token、audience 和 scope |

## 3. 功能需求

### FR-01 Tenant Bootstrap

- 系统首次启动接受一次性 bootstrap 配置。
- 创建 Tenant、不可变 `publicTenantKey`、issuer 和 signing key。
- bootstrap 成功后立即关闭 bootstrap 通道。

### FR-01A Management Account

- Bootstrap 创建第一个 Management Account。
- Management Account 与 Tenant End User 分离。
- 管理员通过本地账号密码换取短期 Management Token。
- Management Token audience 为 `roseto-management`，默认 TTL 为 15 分钟。
- 连续登录失败必须限流，用户不存在和密码错误不得产生可区分响应。
- 重复使用 bootstrap 凭据必须失败。

### FR-02 Application Registration

- Tenant Admin 可以注册 Web Application。
- Application 生成唯一 `clientId`。
- 每个 redirect URI 必须精确保存并精确匹配。
- Phase 0 只允许 public client 和 Authorization Code + PKCE。
- 禁止 implicit grant。

### FR-03 Password User

- Tenant Admin 可以创建密码用户。
- 密码使用 Argon2id 哈希，禁止保存明文。
- 用户状态至少包括 `active`、`disabled`。
- 不得通过错误消息区分用户不存在和密码错误。

### FR-04 Authorization Code + PKCE

- `/authorize` 只接受 `response_type=code`。
- 只接受 `code_challenge_method=S256`。
- authorization code 必须短 TTL、一次性消费，并绑定 tenant、client、redirect URI、user 和 PKCE challenge。
- 错误 verifier、错误 redirect URI、错误 client 和 code 重放均拒绝。

### FR-05 Token Issuance

- `/token` 返回 ID Token 和 Access Token。
- ID Token 的 audience 是 `client_id`。
- Access Token 的 audience 是固定 API Resource indicator。
- Phase 0 Access Token TTL 默认 5 分钟。
- Access Token 使用 JWT，签名 header 必须包含 `kid`。

### FR-06 Discovery and JWKS

- 每个 Tenant issuer 暴露 OpenID Discovery。
- JWKS 暴露当前 active key 和 verification-only old keys。
- Key rotation 后新 Token 使用新 `kid`，旧 Token 在保留窗口内仍可验证。

### FR-07 Rose Resource Server

- Rose Resource Server 可以仅通过 `issuer-uri` 验证 Token。
- audience 不匹配返回 401。
- issuer 不匹配返回 401。
- scope 不足返回 403。

## 4. 验收场景

### AC-01 正常登录

```text
创建 Tenant
→ 注册 Web Application
→ 配置 redirect URI
→ 创建密码用户
→ 发起 Authorization Code + PKCE
→ 登录成功
→ 使用 code_verifier 兑换 Token
→ 验证 ID Token、Access Token、JWKS
```

### AC-02 redirect URI 拒绝

任意 scheme、host、port、path 或 query 与已注册 URI 不一致时，授权请求拒绝且不重定向到不受信任地址。

### AC-03 PKCE 拒绝

错误 verifier、缺少 verifier、`plain` method 都必须返回 OAuth 错误。

### AC-04 code 重放拒绝

第一次兑换成功后，第二次使用同一 authorization code 必须失败。

### AC-05 资源授权

有效签名、issuer、audience 和 scope 返回 2xx；scope 不足返回 403；过期 Token 返回 401。

### AC-06 key rotation

轮换后新 Token 使用新 `kid`；旧 Token 在最大 TTL 加时钟偏差窗口内仍能通过 JWKS 验证。

## 5. 非功能要求

- 所有请求生成 `requestId` 和 correlation ID。
- 密码、authorization code、Token、私钥不得写入日志。
- 所有持久化查询显式带 Tenant 上下文。
- 核心 domain/application 测试不得依赖 Spring Context。
- PostgreSQL migration 必须可重复执行；测试使用 Testcontainers。
