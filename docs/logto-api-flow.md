# Logto API 接口流程与实现线索

> 本文根据 Logto 开源仓库当前源码整理，目标是为 Roseto 的 Java 独立实现提供流程依据。本文记录公开 API 行为、源码入口和数据变化，不复制 Logto 源代码。
>
> 源码基线：[`logto-io/logto`](https://github.com/logto-io/logto)。本地研究副本：`/tmp/logto-source`。

文档导航：[文档总索引](README.md)。

本文只负责接口入口、校验、状态变化和实现线索；表结构见 [`logto-source-domain-model.md`](logto-source-domain-model.md)，完整用户时序见 [`logto-user-flow.md`](logto-user-flow.md)。

## 1. API 面分层

Logto 的接口不是一个单一 API，而是四个上下文：

```text
Management API
  管理 Tenant 内的 Application、User、Resource、Role、Organization 等

Experience API
  驱动 Hosted Sign-in Experience 的注册、登录、验证码、MFA 和 interaction

OIDC Provider
  /authorize、/token、/userinfo、Discovery、JWKS、logout

Account API
  已登录终端用户的 profile、password、identity、session 和第三方 Token
```

Experience API 负责完成用户认证交互；OIDC Provider 负责将已完成的认证交互转换为 Authorization Code 和 Token。用户名密码或验证码不是 OAuth password grant。

## 2. Tenant 解析

所有请求首先进入 Tenant 上下文。Tenant 来源可能是 Host、路径或已解析的 OIDC issuer；解析结果决定数据库查询、配置、密钥、用户和 Application 的边界。

```text
HTTP request
  → TenantResolver
  → tenantId / issuer
  → TenantContext
  → domain / query / OIDC provider
```

源码线索：

- `packages/core/src/env-set/index.ts`
- `packages/core/src/env-set/oidc.ts`
- `packages/core/src/tenants/`
- `packages/core/src/middleware/`

实现约束：任何用户、Application、Resource、Role、Session 查询都必须带 Tenant 上下文，不能仅凭全局 ID 查询。

## 3. Application 注册流程

### 3.1 管理 API 请求

```text
POST /api/applications
  → Management API 鉴权
  → 校验 Application 类型
  → 校验 grant types / token endpoint auth method
  → 创建 applications
  → 创建 application_secrets（confidential client 时）
  → 保存 redirect URI 和 OIDC metadata
  → 返回 Application DTO
```

关键校验：

- Application 类型决定允许的认证方式；
- redirect URI 必须按 Application 类型和协议规则校验；
- secret 不应作为普通明文字段长期保存；
- Application 与所有下属配置必须使用同一 `tenant_id`。

源码线索：

- `packages/core/src/routes/applications/application.ts`
- `packages/core/src/routes/applications/application-role.ts`
- `packages/schemas/tables/applications.sql`
- `packages/schemas/tables/application_secrets.sql`

源码入口：[application routes](https://github.com/logto-io/logto/tree/master/packages/core/src/routes/applications)。

## 4. 用户注册 interaction

### 4.1 开始 interaction

```text
GET /authorize?client_id=...&redirect_uri=...&response_type=code&code_challenge=...
  → OIDC Provider 校验 client / redirect / response_type / scope / PKCE
  → 创建或恢复 interaction
  → 跳转 Hosted Sign-in Experience
```

### 4.2 识别 identifier

```text
POST Experience interaction identifier
  → 校验 identifier 类型和 Sign-in Experience 策略
  → 查找 Tenant-local User
  → 决定进入 sign-in 或 sign-up
  → 记录 interaction 状态
```

### 4.3 验证码注册

```text
POST verification-code/send
  → 校验 identifier 和用途
  → 生成短时验证码
  → 写入 verification record
  → 调用 Email / SMS Connector

POST verification-code/verify
  → 校验 tenant、interaction、purpose、TTL、attempts
  → 消费 verification record
  → 标记 identifier 已验证

POST interaction/submit
  → 校验必填 profile / password / 验证状态
  → 创建 users
  → 创建 password 或外部 User Identity
  → 创建登录 Session
  → 完成 interaction
```

cle

注册时 `users.tenant_id` 来自 Application / OIDC 请求所属 Tenant。验证码验证成功后不会直接返回 Access Token，而是继续完成 interaction，最终生成 Authorization Code。

源码线索：

- `packages/core/src/routes/interaction/index.ts`
- `packages/core/src/routes/interaction/identifier-verification.ts`
- `packages/core/src/routes/interaction/verifications/identifier-verification.ts`
- `packages/core/src/routes/interaction/actions/submit-interaction.ts`
- `packages/core/src/routes/experience/verification-routes/verification-code.ts`
- `packages/schemas/tables/users.sql`
- `packages/schemas/tables/verification_records.sql`

源码入口：[interaction routes](https://github.com/logto-io/logto/tree/master/packages/core/src/routes/interaction)。

## 5. 用户登录 interaction

### 5.1 密码登录

```text
GET /authorize
  → 创建 interaction
  → Hosted Sign-in Experience

POST sign-in identifier
  → 找到 Tenant-local User
  → 检查 User / identity 状态

POST sign-in password
  → 读取 PasswordCredential
  → 验证密码和密码策略
  → 进入 MFA / additional verification（如启用）

POST interaction/submit
  → 创建或更新 UserSession
  → 生成 Authorization Code
  → redirect 回 Application
```

Logto 的密码只提交给 Hosted Sign-in Experience，不由业务 Application 收集；此流程不是 `grant_type=password`。

### 5.2 验证码登录

```text
输入 Email / Phone
  → 发送验证码
  → 校验验证码
  → 找到或按策略创建 User
  → 完成 MFA / profile 要求
  → 创建 Session
  → 生成 Authorization Code
```

验证码必须绑定 Tenant、identifier、interaction 和用途，并且短 TTL、单次消费、限制尝试次数。源码中验证码和交互验证分散在 verification routes 与 interaction verification classes 中。

## 6. Authorization Code + PKCE

### 6.1 `/authorize`

```text
GET /oidc/auth（或租户 OIDC authorize endpoint）
  → 解析 Tenant / issuer
  → 查找 Application
  → 校验 client_id
  → 校验 redirect_uri 精确匹配
  → 校验 response_type=code
  → 校验 scope / resource
  → 校验 state / nonce
  → 校验 code_challenge
  → 若未认证则进入 interaction
  → 认证完成后生成一次性 Authorization Code
  → code 绑定 tenant、client、redirect_uri、user、PKCE、TTL
  → redirect_uri?code=...&state=...
```

### 6.2 `/token` authorization_code

```text
POST /oidc/token
  grant_type=authorization_code
  code=...
  redirect_uri=...
  code_verifier=...

  → 校验 Client authentication（按 Application 配置）
  → 查找 Authorization Code
  → 校验 code 未消费、未过期、Tenant 一致
  → 校验 client_id、redirect_uri、PKCE verifier
  → 原子消费 Authorization Code
  → 创建 Grant / Session 关联
  → 签发 ID Token、Access Token
  → 按配置签发 Refresh Token
```

关键不变量：Authorization Code 只能消费一次；失败或 replay 不应再次签发 Token。

源码线索：

- `packages/core/src/oidc/init.ts`
- `packages/core/src/oidc/interaction.ts`
- `packages/core/src/oidc/adapter.ts`
- `packages/core/src/oidc/grants/`
- `packages/core/src/event-listeners/authorization-success.ts`
- `packages/schemas/tables/oidc_model_instances.sql`

OIDC Provider 由 [`packages/core/src/oidc`](https://github.com/logto-io/logto/tree/master/packages/core/src/oidc) 组装，底层 Provider 负责标准协议路由和 Grant 生命周期。

## 7. Token 流程

### 7.1 ID Token

```text
OIDC authorization 成功
  → 读取 User / Application / OIDC 配置
  → 生成 claims
  → 使用当前 Signing Key 签名
  → header 带 kid
```

ID Token 给 Client 使用，不应作为业务 API Access Token。

### 7.2 JWT Access Token

当请求了具体 Resource 时：

```text
resource indicator
  → 查找 Tenant Resource
  → 计算 scope / permission
  → 生成 aud=resource.indicator 的 Access Token
  → 使用 Signing Key 签名
```

Resource Server 应检查：

```text
signature、iss、aud、exp、scope
```

### 7.3 Opaque Access Token

未指定 Resource 时，Logto 可以返回 opaque Access Token。Resource Server 不能只依赖 JWKS 验证，而需要使用 introspection。Roseto 第一阶段可以固定 Resource Access Token 为 JWT，但必须保留该行为差异。

### 7.4 Refresh Token rotation

```text
POST /oidc/token
  grant_type=refresh_token
  refresh_token=...

  → 查找 Refresh Token Family
  → 校验未撤销、未过期、client / user / tenant 一致
  → 检查旧 token 是否已使用
  → replay 时撤销整个 family
  → 正常使用时撤销旧 token
  → 创建新 Refresh Token
  → 签发新 Access Token / ID Token
```

源码线索：

- `packages/core/src/oidc/grants/refresh-token.ts`
- `packages/core/src/oidc/grants/refresh-token.test.ts`
- `packages/schemas/tables/oidc_model_instances.sql`

## 8. Discovery、JWKS、UserInfo、Logout

### Discovery

```text
GET /.well-known/openid-configuration
  → 根据 issuer / tenant 读取 OIDC 配置
  → 返回 authorization_endpoint、token_endpoint、userinfo_endpoint、jwks_uri、scopes_supported 等
```

源码线索：

- `packages/core/src/routes/well-known/index.ts`
- `packages/core/src/routes/well-known/well-known.openapi.json`
- `packages/core/src/routes/public-wellknown.ts`

### JWKS

```text
GET /oidc/jwks
  → 读取当前和 rotation grace period 内的公钥
  → 返回 JWK Set
```

旧公钥在 grace period 内保留，以便旧 Access Token 继续验证。私钥不应返回。

源码线索：

- `packages/core/src/oidc/init.ts`
- `packages/core/src/oidc.ts`
- `packages/schemas/tables/oidc_model_instances.sql`

### UserInfo

```text
GET /oidc/me 或 userinfo endpoint
  Authorization: Bearer access_token

  → 验证 Access Token 或执行 introspection
  → 根据 sub 查找 Tenant-local User
  → 按 scope 选择 claims
  → 返回 OIDC UserInfo
```

UserInfo 返回用户 claims；ID Token 不用于调用业务 API。

### Logout

```text
GET /oidc/session/end
  → 校验 logout hint / client / post_logout_redirect_uri
  → 清理浏览器 Session
  → 按策略撤销 Grant / Refresh Token
  → 返回合法的 post logout redirect
```

## 9. Management API 的共同处理链

Management API 的大多数资源遵循同一条链路：

```text
HTTP request
  → Bearer Access Token
  → issuer / audience 校验
  → Management API scope 校验
  → Tenant context 校验
  → DTO schema 校验
  → domain/service 操作
  → repository 查询或事务写入
  → hooks / audit log
  → response DTO
```

典型资源流程：

```text
POST /api/users
  → 校验 management:user:create
  → 检查 tenant_id
  → 校验 identifier 唯一性
  → 创建 User / PasswordCredential / Identity

POST /api/resources
  → 校验 resource:create
  → 校验 indicator 在 Tenant 内唯一
  → 创建 Resource

POST /api/roles
  → 创建 Tenant-local Role
  → 后续通过 role-scope / user-role / application-role 关系授予权限

POST /api/organizations
  → 创建 Organization
  → 创建或使用 Tenant 的 Organization Template
  → 后续通过 membership 和 organization role 授权
```

源码入口：

- `packages/core/src/routes/user/`
- `packages/core/src/routes/resource.ts`
- `packages/core/src/routes/scope.ts`
- `packages/core/src/routes/role.ts`
- `packages/core/src/routes/organization/`
- `packages/core/src/routes/application/`

完整路由索引位于 [`packages/core/src/routes`](https://github.com/logto-io/logto/tree/master/packages/core/src/routes)。

## 10. Account API

Account API 是已登录终端用户的自助 API，不等同于 Management API：

```text
Bearer User Access Token
  → 从 sub 得到当前 User
  → 禁止客户端指定任意 user_id
  → 只能修改当前用户允许修改的字段
```

典型流程：

```text
GET /api/my-account
  → 解析当前 User
  → 返回 profile / identities / security 状态

更新 password / identity
  → 校验当前 Session 或 re-authentication
  → 更新 credential / identity
  → 必要时撤销其他 Session

删除或撤销 Session
  → 校验 Session 属于当前 User
  → 标记 session / grant / refresh family 撤销
```

源码线索：

- `packages/core/src/routes/account/`
- `packages/account/src/`
- `packages/schemas/tables/sessions.sql`

## 11. Roseto Java 实现顺序

建议不要从所有 Management API 开始，而按一条可运行链实现：

```text
1. Tenant bootstrap
2. Application + redirect URI
3. User + PasswordCredential
4. Experience interaction
5. /authorize + PKCE
6. /token authorization_code
7. Discovery + JWKS
8. JWT Access Token + UserInfo
9. Resource Server 验证
10. Refresh Token rotation
11. Logout / revoke
12. Management API
13. Organization / Connector / MFA
```

Java 后端保持三个 Maven 模块，API 面作为 `roseto-app` 内的 package：

```text
roseto-domain
  domain + use cases + ports

roseto-infrastructure
  MyBatis Plus + PostgreSQL + Redis + OIDC + key store + Flyway

roseto-app
  management + experience + account + oidc controllers + static resources
```

## 12. 当前覆盖边界

本文覆盖核心 API 调用链和实现入口，但没有逐一抄录所有 Management API 的字段级 OpenAPI 定义，也没有完整展开 SAML、SCIM、Device Authorization、Token Exchange、Webhook、MCP 和所有 Connector 回调。实现这些功能前，应继续以对应 route、OpenAPI JSON、integration test 和 query 文件为准。
